package com.aadeshwagh.ContentWiz;

import com.aadeshwagh.ContentWiz.util.FileServer;
import com.aadeshwagh.ContentWiz.upload.UploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ContentWizApplication implements CommandLineRunner {

	@Autowired
	FileServer fileServer;

	@Autowired
	UploadService uploadService;

	public static void main(String[] args) {

		SpringApplication.run(ContentWizApplication.class, args);

	}

	@Override
	public void run(String... args) throws Exception {
		fileServer.getPublicBaseUrl();
		uploadService.publishAllTypes();
	}
}
