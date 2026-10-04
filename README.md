# ContentWiz

> An automated, AI-driven pipeline that turns a topic into a finished video — script, visuals, and voiceover — then publishes it to YouTube and Instagram.

ContentWiz is a Spring Boot application that automates short- and long-form video content end to end. It generates the script, the visuals, and the narration with AI, assembles them into a finished video, and uploads it to the right channels — so an entire content workflow runs with minimal hands-on work.

## How it works

The pipeline runs in stages:

1. **Script** — generates a style-specific script with Google Gemini (Flash).
2. **Visuals** — generates art-style-specific images, plus a thumbnail derived from the description, with Gemini's image model.
3. **Voiceover** — produces narration with Chatterbox TTS, run locally via ONNX Runtime.
4. **Assembly** — combines the generated images and audio into a finished video.
5. **Publish** — uploads by content *type*: shorts go to both YouTube and Instagram, long-form goes to YouTube. Titles, hashtags, descriptions, and thumbnails are read from per-folder metadata files.

## Content model

- Content is organized by **type** (e.g. `motivation`, `finance`), each containing `long/` and `short/` folders.
- Each folder carries a metadata file (title, video path, hashtags, description, thumbnail) used at upload time.
- Channel names, account handles, and credentials live in the properties file.

Planned channels: a **niche** channel (finance and other niches) and a **lore** channel (stories and recaps), with matching shorts and reels.

## Tech stack

- **Java 21** · **Spring Boot 4**
- **Google Gen AI SDK (Gemini)** — script and image generation
- **Chatterbox TTS** via **ONNX Runtime** — voiceover
- **java-ngrok** — tunneling for upload/auth callbacks
- **Lombok** · **Maven**

## Getting started

### Prerequisites
- Java 21+
- Maven
- A Google Gemini API key (image generation uses the paid Gemini API)

### Configure
Add your Gemini API key and your channel/account credentials to `src/main/resources/application.properties`.

### Build & run

```bash
git clone https://github.com/aadeshwagh/ContentWiz.git
cd ContentWiz
mvn spring-boot:run
```

Or build a runnable jar:

```bash
mvn clean package
java -jar target/ContentWiz-0.0.1-SNAPSHOT.jar
```

## Status

Actively in development. The generation pipeline — script, image, TTS, and video assembly — is working; multi-platform upload is in progress.
