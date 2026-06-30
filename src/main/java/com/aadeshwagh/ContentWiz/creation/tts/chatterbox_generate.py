#!/usr/bin/env python3

import argparse
import sys
import os
import json
import tempfile

VOICES_DIR = "/Users/aadeshwagh/ContentWiz/src/main/resources/voices/chatterbox-tts-voices/prompts"

# Best available resampler in torchaudio — sinc_interp_kaiser minimises aliasing
RESAMPLE_METHOD = "sinc_interp_kaiser"


def resolve_voice_path(voice_name: str) -> str | None:
    name = voice_name if voice_name.lower().endswith(".wav") else f"{voice_name}.wav"
    path = os.path.join(VOICES_DIR, name)
    return path if os.path.isfile(path) else None


def parse_args():
    parser = argparse.ArgumentParser(
        description="Chatterbox TTS (full model) — highest quality batch scene generator",
        formatter_class=argparse.RawTextHelpFormatter,
    )

    voice_group = parser.add_mutually_exclusive_group()
    voice_group.add_argument(
        "--voice",
        metavar="VOICE_NAME",
        help="Built-in voice name (e.g. Lucy). Resolves to VOICES_DIR/<name>.wav",
    )
    voice_group.add_argument(
        "--voice_ref",
        metavar="PATH",
        help="Custom reference WAV path for voice cloning.",
    )

    # Batch mode
    parser.add_argument(
        "--scenes-json",
        metavar="JSON",
        help=(
            'JSON array of scenes:\n'
            '  \'[{"sceneNumber":1,"narration":"Hello"},...]\'\n'
            "Each scene saved as <output-folder>/<sceneNumber>.wav.\n"
            "All scenes in the batch share the same --exaggeration / --temperature / "
            "--cfg_weight (the script's single emotion)."
        ),
    )
    parser.add_argument(
        "--output-folder",
        metavar="DIR",
        help="Output directory for batch mode (required with --scenes-json).",
    )

    # Single mode (CLI testing)
    parser.add_argument("--text",         help="Single narration text.")
    parser.add_argument("--output_path",  help="Output WAV path for single mode.")

    # Emotion params — applied once, shared across every scene in batch mode
    parser.add_argument("--exaggeration", type=float, default=0.5,
                        help="Emotion intensity 0.0-2.0 (default: 0.5)")
    parser.add_argument("--temperature",  type=float, default=0.8,
                        help="Sampling temperature 0.0-2.0 (default: 0.8)")
    parser.add_argument("--cfg_weight",   type=float, default=0.5,
                        help="Classifier-free guidance strength 0.0-1.0 (default: 0.5)")

    return parser.parse_args()


def sanitise_ref_audio(path: str, model_sr: int) -> str:
    """
    Prepare reference audio for the model:
      - Load at native SR, convert to mono float32
      - Resample to model.sr using sinc_interp_kaiser (best quality kernel)
        only if the source SR differs — no unnecessary resample if already correct
      - Write as 32-bit float PCM WAV via soundfile (bypasses TorchCodec which
        silently ignores encoding params and causes float64 reload crashes)

    model_sr is read from model.sr after loading — never hardcoded here.
    """
    import torch
    import torchaudio as ta
    import torchaudio.transforms as T
    import soundfile as sf
    import numpy as np

    wav, sr = ta.load(path)
    wav = wav.to(torch.float32)

    print(
        f"INFO: Voice ref loaded | sr={sr}Hz | channels={wav.shape[0]} | "
        f"duration={wav.shape[1] / sr:.2f}s",
        file=sys.stderr,
    )

    # Mix to mono — model expects single channel
    if wav.shape[0] > 1:
        wav = wav.mean(dim=0, keepdim=True)
        print("INFO: Converted stereo → mono", file=sys.stderr)

    # Resample only if needed, using the highest quality kernel available
    if sr != model_sr:
        print(
            f"INFO: Resampling {sr}Hz → {model_sr}Hz (method={RESAMPLE_METHOD}, "
            f"filter_width=64)",
            file=sys.stderr,
        )
        resampler = T.Resample(
            orig_freq=sr,
            new_freq=model_sr,
            resampling_method=RESAMPLE_METHOD,
            lowpass_filter_width=64,        # wider = fewer aliasing artefacts
            rolloff=0.9475937167399596,     # optimal Kaiser rolloff
            dtype=torch.float32,
        )
        wav = resampler(wav)
    else:
        print(f"INFO: Voice ref already at {model_sr}Hz — no resampling needed", file=sys.stderr)

    # Write to temp file as true 32-bit float PCM
    tmp = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
    tmp.close()
    sf.write(tmp.name, wav.squeeze(0).numpy().astype(np.float32), model_sr, subtype="FLOAT")

    print(
        f"INFO: Sanitised ref → {tmp.name} | shape={wav.shape} | "
        f"duration={wav.shape[1] / model_sr:.2f}s",
        file=sys.stderr,
    )
    return tmp.name


def load_model():
    """
    Load full ChatterboxTTS (500M params, honours exaggeration + cfg_weight).

    Device strategy:
      1. Load entirely to CPU first (safe deserialisation).
      2. Cast every sub-model to float32 (some load as float64 on CPU).
      3. Attempt to move all sub-models to MPS.
         - If ANY sub-model fails MPS migration (e.g. slow_conv2d has no MPS
           kernel), roll back EVERYTHING to CPU. A mixed-device state is what
           caused the original crash: speaker_encoder on MPS but the ref-audio
           tensor arriving from CPU.
      4. Explicitly set model.device to the final chosen device.
         Chatterbox's prepare_conditionals passes device=self.device into
         embed_ref, which is what actually moves the ref-audio tensor.
         If model.device is still "cpu" but weights are on MPS → crash.

    model.sr is read after loading and passed downstream — never hardcoded.
    """
    import torch
    import torch.nn as nn
    from chatterbox.tts import ChatterboxTTS

    print("INFO: Loading ChatterboxTTS (full model) to cpu...", file=sys.stderr)
    model = ChatterboxTTS.from_pretrained(device="cpu")

    # Cast all sub-models to float32 before any device move
    for attr_name, attr_val in vars(model).items():
        if isinstance(attr_val, nn.Module):
            attr_val.to(torch.float32)
            print(f"INFO: Cast model.{attr_name} → float32", file=sys.stderr)

    target_device = "cpu"  # default — overridden below if MPS fully succeeds

    if torch.backends.mps.is_available():
        print("INFO: MPS available — attempting to move all sub-models to mps...", file=sys.stderr)

        failed_modules: list[str] = []
        for attr_name, attr_val in vars(model).items():
            if isinstance(attr_val, nn.Module):
                try:
                    attr_val.to("mps")
                    print(f"INFO:   model.{attr_name} → mps ✓", file=sys.stderr)
                except Exception as e:
                    failed_modules.append(attr_name)
                    print(f"WARN:   model.{attr_name} could not move to mps: {e}", file=sys.stderr)

        if failed_modules:
            # Mixed-device state is dangerous — roll everything back to CPU.
            # This is exactly what caused the original crash.
            print(
                f"WARN: {len(failed_modules)} sub-model(s) rejected MPS "
                f"({failed_modules}). Rolling back ALL sub-models to CPU to "
                f"avoid cross-device tensor errors.",
                file=sys.stderr,
            )
            for attr_name, attr_val in vars(model).items():
                if isinstance(attr_val, nn.Module):
                    attr_val.to("cpu")
            target_device = "cpu"
        else:
            print("INFO: All sub-models successfully on MPS.", file=sys.stderr)
            target_device = "mps"
    else:
        print("INFO: MPS not available — running entirely on cpu.", file=sys.stderr)

    # -------------------------------------------------------------------------
    # CRITICAL FIX:
    # Chatterbox's prepare_conditionals does:
    #   self.s3gen.embed_ref(s3gen_ref_wav, S3GEN_SR, device=self.device)
    # embed_ref moves the ref-audio tensor to `device` before the forward pass.
    # If model.device != the device the weights are actually on → crash.
    # We must explicitly sync model.device to where we actually put the weights.
    # -------------------------------------------------------------------------
    model.device = torch.device(target_device)
    print(f"INFO: Model ready | sr={model.sr}Hz | device={model.device}", file=sys.stderr)
    return model


def generate_one(
    model,
    text: str,
    exaggeration: float,
    temperature: float,
    cfg_weight: float,
    ref_path: str | None,
    output_path: str,
):
    """
    Generate a single audio clip and save it as 16-bit PCM WAV.
    16-bit at model.sr is transparent for speech — adding more bits doesn't
    improve perceived quality above the model's own output resolution.
    """
    import torch
    import soundfile as sf
    import numpy as np

    generate_kwargs = {
        "exaggeration": exaggeration,
        "temperature":  temperature,
        "cfg_weight":   cfg_weight,
    }
    if ref_path:
        generate_kwargs["audio_prompt_path"] = ref_path

    wav = model.generate(text, **generate_kwargs)

    if isinstance(wav, torch.Tensor):
        wav = wav.to(torch.float32).cpu()

    out_np = wav.squeeze(0).numpy().astype(np.float32)
    sf.write(output_path, out_np, model.sr, subtype="PCM_16")
    print(f"OK: Saved → {output_path}", file=sys.stderr)


def run_batch(
    model,
    scenes: list,
    output_folder: str,
    ref_path: str | None,
    exaggeration: float,
    temperature: float,
    cfg_weight: float,
):
    """
    Generate audio for every scene using ONE shared exaggeration / temperature /
    cfg_weight — the emotion now applies to the whole script, not per scene.
    """
    os.makedirs(output_folder, exist_ok=True)
    total = len(scenes)

    print(
        f"INFO: Batch emotion params (applied to all {total} scenes) | "
        f"exag={exaggeration} temp={temperature} cfg={cfg_weight}",
        file=sys.stderr,
    )

    for i, scene in enumerate(scenes, start=1):
        scene_number = scene.get("sceneNumber", i)
        narration    = scene.get("narration", "").strip()

        if not narration:
            print(f"WARN: Scene {scene_number} has no narration — skipping", file=sys.stderr)
            continue

        output_path = os.path.join(output_folder, f"{scene_number}.wav")

        print(f"INFO: [{i}/{total}] Scene {scene_number}", file=sys.stderr)

        generate_one(
            model, narration, exaggeration, temperature,
            cfg_weight, ref_path, output_path,
        )


def run_single(model, args, ref_path: str | None):
    if not args.text or not args.output_path:
        print("ERROR: --text and --output_path are required for single mode.", file=sys.stderr)
        sys.exit(1)

    output_dir = os.path.dirname(args.output_path)
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)

    generate_one(
        model, args.text, args.exaggeration, args.temperature,
        args.cfg_weight, ref_path, args.output_path,
    )


def main():
    args = parse_args()

    # Resolve voice source
    ref_audio_path: str | None = None
    if args.voice:
        resolved = resolve_voice_path(args.voice)
        if not resolved:
            print(f"ERROR: Voice '{args.voice}' not found in {VOICES_DIR}", file=sys.stderr)
            sys.exit(1)
        ref_audio_path = resolved
        print(f"INFO: Using built-in voice '{args.voice}' → {resolved}", file=sys.stderr)
    elif args.voice_ref:
        if not os.path.isfile(args.voice_ref):
            print(f"ERROR: --voice_ref not found: {args.voice_ref}", file=sys.stderr)
            sys.exit(1)
        ref_audio_path = args.voice_ref
        print(f"INFO: Using custom voice ref: {ref_audio_path}", file=sys.stderr)

    batch_mode = args.scenes_json is not None
    if batch_mode and not args.output_folder:
        print("ERROR: --output-folder is required when using --scenes-json", file=sys.stderr)
        sys.exit(1)

    try:
        import torch
        import soundfile
    except ImportError as e:
        print(f"ERROR: Missing dependency: {e}", file=sys.stderr)
        print("Run: pip install chatterbox-tts torch torchaudio soundfile peft", file=sys.stderr)
        sys.exit(1)

    tmp_ref_path: str | None = None
    try:
        # Load model first so we can read model.sr before sanitising ref audio
        model = load_model()

        # Sanitise ref audio once using model.sr — reused across all scenes
        if ref_audio_path:
            tmp_ref_path = sanitise_ref_audio(ref_audio_path, model.sr)
            active_ref = tmp_ref_path
        else:
            active_ref = None

        if batch_mode:
            try:
                scenes = json.loads(args.scenes_json)
            except json.JSONDecodeError as e:
                print(f"ERROR: Invalid --scenes-json: {e}", file=sys.stderr)
                sys.exit(1)
            run_batch(
                model, scenes, args.output_folder, active_ref,
                args.exaggeration, args.temperature, args.cfg_weight,
            )
            print(f"OK: All scenes written to {args.output_folder}", file=sys.stderr)
        else:
            run_single(model, args, active_ref)

        sys.exit(0)

    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)

    finally:
        if tmp_ref_path and os.path.exists(tmp_ref_path):
            os.unlink(tmp_ref_path)
            print("INFO: Cleaned up temp ref file", file=sys.stderr)


if __name__ == "__main__":
    main()