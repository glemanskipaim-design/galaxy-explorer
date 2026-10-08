package dev.galaxyexplorer.catalog.image;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ImageLicenseRepository {

    private final JdbcClient jdbc;

    public ImageLicenseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ImageLicense> findByCode(String code) {
        return jdbc.sql("SELECT code, name, url FROM image_license WHERE code = :code")
                .param("code", code)
                .query((rs, n) -> new ImageLicense(rs.getString("code"), rs.getString("name"), rs.getString("url")))
                .optional();
    }
}
