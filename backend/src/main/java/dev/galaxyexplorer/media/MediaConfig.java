package dev.galaxyexplorer.media;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaProperties.class)
public class MediaConfig {

    @Bean
    ImageFileStore imageFileStore(MediaProperties props) {
        return new ImageFileStore(props.storageDir());
    }

    @Bean
    JpegInspector jpegInspector(MediaProperties props) {
        return new JpegInspector(props.maxPixels());
    }

    @Bean
    Thumbnailer thumbnailer(MediaProperties props) {
        return new Thumbnailer(props.thumbMaxSize());
    }
}
