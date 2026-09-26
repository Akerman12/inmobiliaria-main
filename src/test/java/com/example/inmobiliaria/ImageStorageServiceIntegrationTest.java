package com.example.inmobiliaria;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.inmobiliaria.Service.ImageStorageService;
import com.mongodb.client.gridfs.model.GridFSFile;

@SpringBootTest
@AutoConfigureMockMvc
class ImageStorageServiceIntegrationTest {

    @Autowired
    private ImageStorageService imageStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void storesImageInMongoAndServesItPubliclyWithCache() throws Exception {
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        ImageIO.write(image, "png", source);

        String propertyId = UUID.randomUUID().toString();
        String id = imageStorageService.store(new MockMultipartFile(
            "imagenes", "integration-house.png", "image/png", source.toByteArray()),
            propertyId, "Casa de prueba", 1);
        try {
            GridFSFile savedFile = imageStorageService.findById(id);
            assertNotNull(savedFile);
            assertEquals(propertyId, savedFile.getMetadata().getString("propertyId"));
            assertEquals("Casa de prueba", savedFile.getMetadata().getString("propertyTitle"));
            assertEquals(1, savedFile.getMetadata().getInteger("imageNumber"));
            assertEquals("integration-house.png", savedFile.getMetadata().getString("originalFilename"));
            assertTrue(savedFile.getFilename().contains("casa-de-prueba-imagen-1"));

            MvcResult result = mockMvc.perform(get("/api/imagenes/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                            org.hamcrest.Matchers.containsString("max-age=31536000")))
                    .andReturn();

            BufferedImage stored = ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
            assertNotNull(stored);
            assertTrue(stored.getWidth() <= 1600);
        } finally {
            imageStorageService.deleteByReference("/api/imagenes/" + id);
        }
    }
}