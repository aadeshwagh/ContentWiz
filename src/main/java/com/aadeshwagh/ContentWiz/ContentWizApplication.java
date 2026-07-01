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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tools.jackson.databind.ObjectMapper;

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
		//fileServer.getPublicBaseUrl();
		//uploadService.publishVideosForType("wakethefuckup");
//		Script script = objectMapper.readValue(new File("content/wakethefuckup/story-script.json"),Script.class);
//		long startTime = System.nanoTime();
//		imageGenService.generateImages(script,"src/main/java/com/aadeshwagh/ContentWiz/creation/prompts/stick-figure-art-style-prompt.txt","/Users/aadeshwagh/ContentWiz/content/wakethefuckup/workdir");
//		chatterboxTtsService.generateForScript(script, Emotion.SINCERE ,"src/main/resources/voices/dan.wav", "/Users/aadeshwagh/ContentWiz/content/wakethefuckup/workdir");
//		videoAssemblyService.assembleVideo(script,"/Users/aadeshwagh/ContentWiz/content/wakethefuckup/workdir","/Users/aadeshwagh/ContentWiz/content/wakethefuckup/long");
//		List<Shorts> shorts = scriptGenerator.generateShorts(script);
//		videoAssemblyService.assembleShortVideos(script,shorts,"/Users/aadeshwagh/ContentWiz/content/wakethefuckup/workdir","/Users/aadeshwagh/ContentWiz/content/wakethefuckup/short");
//		metaDataService.addMetaDataForLongScript(script,"wakethefuckup");
//		metaDataService.addMetaDataForShortScript(shorts,"wakethefuckup");
//
//		long endTime = System.nanoTime();
//		long durationNano = endTime - startTime;
//
//		// 4. Convert to seconds (divide by 1 billion using a double for decimal accuracy)
//		double durationSeconds = (double) durationNano / 1_000_000_000;
//
//		// 5. printtime
//		System.out.printf("Total execution time: %.3f seconds%n", durationSeconds);
		String synopics= "Against the backdrop of a war between humans and robots with artificial intelligence, a former soldier finds the robots' secret weapon to end the conflict, an AI in the form of a child.\n" +
				"Amid a future war between the human race and the forces of artificial intelligence, Joshua, a hardened ex-special forces agent grieving the disappearance of his wife, is recruited to hunt down and kill the Creator, the elusive architect of advanced AI who has developed a mysterious weapon with the power to end the war-and mankind itself.\n" +
				"—20th Century Studios\n" +
				"In the near future, after a devastating nuclear accident in Los Angeles, the USA not only bans AI, but wages a veritable war against it. Things are very different in so-called \"New Asia\", where man and machine live in peaceful co-existence. Or try to live, because the US Army, with its Nomad combat station floating in the stratosphere, is waging its battle globally and regardless of losses. But the military superiority of the USA is threatened by a new wonder weapon. Once an undercover agent, Joshua (John David Washington), wounded in battle, leads the squad of elite soldiers under Colonel Howell (Allison Janney) into the factory known only to him. In a fiercely contested battle, Joshua manages to penetrate the safe and steal the weapon that will decide the war: It's a child.\n" +
				"—SRF\n" +
				"Synopsis\n" +
				"In 2055, an artificial intelligence (AI) detonates a nuclear warhead over Los Angeles, California. In response, most of the Western nations wage war against AI to prevent humanity's extinction; they are being resisted by people of the so-called New Asia (consisting of Southeast Asia, Japan, Taiwan, Bangladesh, Bhutan, Nepal and parts of India) who continue to embrace AI. The military seeks to assassinate \"Nirmata\", the mysterious chief architect behind the AI advancements. The USS NOMAD (North American Orbital Mobile Aerospace Defense) is developed as an advanced space station capable of launching destructive attacks from orbit.\n" +
				"\n" +
				"U.S. Army sergeant Joshua Taylor is an undercover operative with his pregnant wife Maya Fey, whom the military believes to be the daughter of Nirmata. When military forces attack their home, exposing Taylor as a covert agent seeking to use Maya to find Nirmata, Maya runs away but is hit by a subsequent NOMAD strike.\n" +
				"\n" +
				"Five years later, Taylor works as part of the ground zero cleanup crew in Los Angeles. He is approached by General Andrews and Colonel Howell to join a mission to destroy a new weapon engineered by Nirmata, \"Alpha O\", believed to be capable of destroying NOMAD and thus shifting the balance of the war in favor of AI. To recruit him, they play a video showing Maya alive, and suggest he might find and reunite with her if he joins the team. In New Asia, separated from the rest of the strike team, Taylor manages to enter the compound believed to hold the weapon but discovers only a robotic \"simulant\" in the form of a young girl. It is revealed that the girl has the ability to remotely control technology. Dubbing her \"Alphie\", Taylor disobeys Howell's orders to kill Alphie and the two travel to find Drew, Taylor's former commanding officer.\n" +
				"\n" +
				"Examining Alphie, Drew tells Taylor she is capable of becoming the most powerful weapon on the planet as her abilities to control technology will grow exponentially. New Asian police attack Drew's apartment, killing his simulant girlfriend Kami, as Howell and squad member McBride close in. With Drew's help, Taylor locates Maya's beacon but does not find her before being attacked, and Drew is fatally wounded protecting him. Before Drew dies, he tells Taylor that the raid five years earlier had happened because of intelligence gathered that Maya was Nirmata. Taylor and Alphie are captured by New Asian forces led by Harun, a simulant soldier and former ally of Taylor's.\n" +
				"\n" +
				"Harun states that the detonation in Los Angeles was caused by a human coding error, and that the U.S. government unfairly cast the blame on AI, who only wish to peacefully co-exist with humanity. After escaping his captors, Taylor rescues Alphie and prepares to flee as Howell leads an attack on the village. Alphie intervenes with her abilities but is gravely wounded by McBride. She is rushed to Maya, who Taylor learns has been in a coma since the strike on her home, tended to by simulant monks. Because simulants cannot harm Nirmata, she is \"stranded\" and unable to die. It is also revealed that Alphie was based on Taylor and Maya's unborn daughter, who had been scanned in utero. Distressed, Taylor takes Maya off life support as Howell and her forces arrive. They are killed by Harun, who tells Taylor NOMAD must be destroyed in order for the war to end.\n" +
				"\n" +
				"Taylor and Alphie are captured by U.S. forces and taken to Los Angeles, where Taylor is forced to kill Alphie with an electro magnetic pulse weapon. However, Andrews later discovers this to be a ruse, and the pair escape before Alphie can be incinerated. Boarding a lunar shuttle at the Los Angeles Interplanetary Air and Space Port, Alphie forces the spacecraft to land aboard NOMAD just as Andrews orders a large-scale assault on remaining AI bases across the globe. Taylor plants a timed explosive while Alphie disables the ship's power. Before Taylor can arrive at the escape pod, Andrews activates a tentacled robot that prevents him from entering, and Taylor is forced to eject the vehicle with Alphie in it. As NOMAD explodes, halting the strike, Taylor embraces a simulant bearing Maya's likeness, whom Alphie had activated using a memory chip containing information Howell had downloaded from Maya's brain just after she died. Taylor spends his last few moments with Maya as NOMAD explodes, killing them both, while Alphie returns to Earth and witnesses the people celebrating NOMAD's destruction while cheering her as Nirmata.";
		MovieRecapScript script = objectMapper.readValue(new File("content/movierecap/creator-recap-script.json"),MovieRecapScript.class);
		//chatterboxTtsService.generateForMovieScript(script,Emotion.NEUTRAL,"src/main/resources/voices/dan.wav","/Users/aadeshwagh/ContentWiz/content/movierecap/workdir");
		recapVideoAssembly.assemble("content/movierecap/The.Creator.2023.720p.WEBRip.x264.AAC-[YTS.MX].mp4",script,"/Users/aadeshwagh/ContentWiz/content/movierecap/long","/Users/aadeshwagh/ContentWiz/content/movierecap/workdir");

	}



}
