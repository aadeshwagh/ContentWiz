ContentWiz

Phase 1

current requirements
feature/upload

1. System should be able to pick from type given in properties files then long/short videos and upload them to the channel mentioned for that type
    for eg. type motivation is there. There will be folder named motivation inside that two folders long and short then there will be property named channel name 
    for channels of motivation type one for instagram and one for youtube then all the videos in short folder under perticular type will get uploaded to the instagram and youtube both
    and videos in long will only get uploaded to youtube now each folder long and short will have a file json/text which ever is easy that will contain all the information of the videos in that folder for youtube and instagram which is needed to upload
    like title what video path in the same folder, hashtags and description thumbnail path ( this too will be in same folder ) and other stuff . all the passwords and account names will be in properties file 

2. lets first focus on what content work and what channel
    1. fist focus on youtube channel and its content
   
3. Lets create the 2 youtube channels 
   1. The niche channel - finance and other niche stuff
   2. the lore channel - stories, recaps and stuff
   3. then create shots and reels corresponding to that

now for creating a channel what is required
1. The video / art style
2. The script
3. the voice over
4. the baground music

script is done - gemini flash
lets see how can i make it better with other models and specialised inputs - done made it style specific
also modify the system prompt to add description field and other if necessary and make - done
the image generation prompt more specific to decided model and art style - done

tts is done - chatterbox
i am satisfied with the quality now search for what type of audio i want also see if 
1. making the emotion constant sounds better or variable is fine
2. if breaking audio in chunks and then sticking sounds better or a continuous flow
3. make the script duration field adjusted to actual audio length

image generation
its done but needed to use the gemini paid api added 1k in tokens lets see how many videos can be made with that
1. see how can you move the static images, or add some sort of revelent gifs on it or so
2. generate a thumbnail based on the description

creating video our of static images and audio files



lets fix the video assembly first - done

fix the tags from script what are supported what not and test it  - done

then the chose the right voice for the channel

create the final video

gaps in TTS - model dosent seem to understand capital words to emphisise them 

remove the mood section completely
