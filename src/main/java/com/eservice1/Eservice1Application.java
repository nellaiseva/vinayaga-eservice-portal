package com.eservice1;

import com.eservice1.config.Msg91Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(Msg91Properties.class)
public class  Eservice1Application {

	public static void main(String[] args) {
		SpringApplication.run(Eservice1Application.class, args);
	}

}
