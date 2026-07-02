package com.aadeshwagh.ContentWiz;

import com.aadeshwagh.ContentWiz.creation.entity.Emotion;
import com.aadeshwagh.ContentWiz.creation.entity.MovieRecapScript;
import com.aadeshwagh.ContentWiz.creation.script.MetaDataService;
import com.aadeshwagh.ContentWiz.creation.script.MovieRecapScriptWriter;
import com.aadeshwagh.ContentWiz.creation.script.ScriptGeneratorGemini;
import com.aadeshwagh.ContentWiz.creation.entity.Script;
import com.aadeshwagh.ContentWiz.creation.entity.Shorts;
import com.aadeshwagh.ContentWiz.creation.imageGen.ImageGenService;
import com.aadeshwagh.ContentWiz.creation.tts.ChatterboxTtsService;
import com.aadeshwagh.ContentWiz.creation.videoAssembly.RecapVideoAssembly;
import com.aadeshwagh.ContentWiz.creation.videoAssembly.VideoAssemblyService;
import com.aadeshwagh.ContentWiz.util.FileServer;
import com.aadeshwagh.ContentWiz.upload.UploadService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;

@SpringBootApplication
public class ContentWizApplication implements CommandLineRunner {

	@Autowired
	FileServer fileServer;

	@Autowired
	UploadService uploadService;

	@Autowired
	ScriptGeneratorGemini scriptGenerator;

	@Autowired
	ChatterboxTtsService chatterboxTtsService;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ImageGenService imageGenService;

	@Autowired
	VideoAssemblyService videoAssemblyService;

	@Autowired
	MetaDataService metaDataService;

	@Autowired
	MovieRecapScriptWriter  movieRecapScriptWriter;

	@Autowired
	RecapVideoAssembly recapVideoAssembly;



	public static void main(String[] args) {

		SpringApplication.run(ContentWizApplication.class, args);

	}

	@Override
	public void run(String... args) throws Exception {
//		fileServer.getPublicBaseUrl();
//		uploadService.publishVideosForType("wakethefuckup");
//		String storyPrompt = "7. \"Your Brain Has a Battery — Here's How to Stop Draining It by Noon\" — energy management framing (more novel than time management). create the big script grater than 8 minutes plus more visuals";
		//scriptGenerator.generateScript(storyPrompt,"/Users/aadeshwagh/ContentWiz/src/main/java/com/aadeshwagh/ContentWiz/creation/prompts/long-video-script-system-prompt-stick-figure-style.txt","/Users/aadeshwagh/ContentWiz/content/wakethefuckup","brain-batery");
//		Script script = objectMapper.readValue(new File("/Users/aadeshwagh/ContentWiz/content/wakethefuckup/brain-batery-script.json"),Script.class);
//		long startTime = System.nanoTime();
//		//imageGenService.generateImages(script,"src/main/java/com/aadeshwagh/ContentWiz/creation/prompts/stick-figure-art-style-prompt.txt","/Users/aadeshwagh/ContentWiz/content/wakethefuckup/workdir");
//		chatterboxTtsService.generateForScript(script, Emotion.SINCERE ,"src/main/resources/voices/dan.wav", "/Users/aadeshwagh/ContentWiz/content/wakethefuckup/workdir");
//		videoAssemblyService.assembleVideo(script,"/Users/aadeshwagh/ContentWiz/content/wakethefuckup/workdir","/Users/aadeshwagh/ContentWiz/content/wakethefuckup/long");
//		// List<Shorts> shorts = scriptGenerator.generateShorts(script);
//		// videoAssemblyService.assembleShortVideos(script,shorts,"/Users/aadeshwagh/ContentWiz/content/wakethefuckup/workdir","/Users/aadeshwagh/ContentWiz/content/wakethefuckup/short");
//		metaDataService.addMetaDataForLongScript(script,"wakethefuckup");
//		//metaDataService.addMetaDataForShortScript(shorts,"wakethefuckup");

//		long startTime = System.nanoTime();
//
//		String synopics= "Two New Orleans paramedics' lives are ripped apart after they encounter a series of horrific deaths linked to a designer drug with bizarre, otherworldly effects.\n" +
//				"Synopsis\n" +
//				"Steve, a ladies' man, and Dennis, a married father, work together as paramedics. They are called out to a series of cases where people are either dead in strange circumstances or whose stories are incoherent. The cases are linked to a new designer drug called Synchronic.\n" +
//				"\n" +
//				"At a domestic abuse call, they find a stabbing victim and an old sword embedded in the wall. While Steve tends to an injured man, he is accidentally stuck by a dirty needle. Being tested for possible infections leads to the discovery of cancer in his underdeveloped and non-calcified pineal gland. The second call, a burn victim, is a completely burned body. The third call is a bite from a venomous snake no longer found in the area.\n" +
//				"\n" +
//				"On a call to a drug party, they find a dead boy, and a girl says there was a third girl, Brianna, Dennis's teenage daughter. The next morning. Steve goes to a local smoke shop and buys all the Synchronic, which he learns is discontinued. As he leaves, Steve declines a man's offer pay triple its worth. The next morning, Steve catches the man breaking into his house. He says he is the chemist who created Synchronic, which alters the pineal gland's perception of time. Children, who have a non-calcified pineal gland, pass through time. Adults seem to only partially move through time like ghosts.\n" +
//				"\n" +
//				"During their next call, a victim of a sword fight dies. Steve, who is a fan of the history of science, quotes Einstein on the meaninglessness of time when faced with his friend's death. Under the stress of Brianna's disappearance, Dennis's marriage deteriorates. When he learns someone has been stealing morphine, he misinterprets Steve's poor health and use of painkillers as evidence he is a morphine addict. The two come to blows while treating a crazed patient.\n" +
//				"\n" +
//				"At home, Steve takes Synchronic, travels back to when the area was covered in a swamp, and is attacked by a conquistador. Steve records his observations, stating Synchronic allows traveling backwards though time for seven minutes in the same geographical location. When he travels back to the ice age, he determines his location when taking the pill determines the destination year.\n" +
//				"\n" +
//				"During his next attempt, he takes back his dog, Hawking. When he moves from the original location due to a hostile man, he loses Hawking and is unable to bring him back. At the location where Brianna disappeared, he discovers several tribal men, who chase him up a tree. He discovers that Brianna may have wandered off before taking Synchronic, and objects from the present can anchor him to the present.\n" +
//				"\n" +
//				"Steve and Dennis talk at a bar. Dennis, who has taken his life for granted, believes he is headed to a divorce. Steve tells Dennis about his cancer, and the two reconcile. Their driver, Tom, was stealing the morphine. At the graveyard of Steve's family, Steve shows Dennis the videos of his time travel, and they deduce that Brianna may have left a message for them to find in the park. Steve travels back to a battlefield during the Battle of New Orleans, is shot in the leg, and searches for Brianna. He finds her in a trench and gives her his last Synchronic pill. They quickly move to the boulder Steve traveled to, where a looter intercepts them and holds Steve at gunpoint, thinking he is a slave. Brianna returns to her future, while Steve is stranded in the past. He appears to become a ghost in front of Dennis. They shake hands and the film ends without knowing if Steve returns to the present.";
//		MovieRecapScript recapScript = movieRecapScriptWriter.generateScript(synopics,"/Users/aadeshwagh/ContentWiz/content/movierecap/Synchronic.2019.1080p.BluRay.x264.AAC5.1-YTS.MX.srt","/Users/aadeshwagh/ContentWiz/content/movierecap","synchronic");
//		chatterboxTtsService.generateForMovieScript(recapScript,Emotion.SINCERE,"src/main/resources/voices/dan.wav","/Users/aadeshwagh/ContentWiz/content/movierecap/workdir");
//		recapVideoAssembly.assemble("/Users/aadeshwagh/ContentWiz/content/movierecap/Synchronic.2019.1080p.WEBRip.x264.AAC5.1-[YTS.MX].mp4",recapScript,"/Users/aadeshwagh/ContentWiz/content/movierecap/long","/Users/aadeshwagh/ContentWiz/content/movierecap/workdir");
//		long endTime = System.nanoTime();
//		long durationNano = endTime - startTime;
//
//		// 4. Convert to seconds (divide by 1 billion using a double for decimal accuracy)
//		double durationSeconds = (double) durationNano / 1_000_000_000;
//
//		System.out.printf("Total execution time: %.3f seconds%n", durationSeconds);
		Script script = objectMapper.readValue(new File("/Users/aadeshwagh/ContentWiz/content/wakethefuckup/brain-batery-script.json"),Script.class);
		List<Shorts> shorts = objectMapper.readValue(
				new File("/Users/aadeshwagh/ContentWiz/content/wakethefuckup/brain-short-script.json"),
				new TypeReference<List<Shorts>>() {}
		);
		//List<Shorts> shorts = objectMapper.readValue(new File("/Users/aadeshwagh/ContentWiz/content/wakethefuckup/brain-short-script.json"),TypeReference<List<Shorts>>(){});
		videoAssemblyService.assembleShortVideos(script,shorts,"/Users/aadeshwagh/ContentWiz/content/wakethefuckup/workdir","/Users/aadeshwagh/ContentWiz/content/wakethefuckup/short");
		metaDataService.addMetaDataForShortScript(shorts,"wakethefuckup");



	}



}
