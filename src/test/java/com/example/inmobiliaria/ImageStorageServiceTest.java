package com.example.inmobiliaria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.bson.types.ObjectId;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.example.inmobiliaria.Service.ImageStorageService;

class ImageStorageServiceTest {

    @Test
    void storesOptimizedJpegInGridFs() throws Exception {
        GridFsTemplate gridFsTemplate = org.mockito.Mockito.mock(GridFsTemplate.class);
        ObjectId storedId = new ObjectId();
        when(gridFsTemplate.store(any(InputStream.class), anyString(), anyString(), any())).thenReturn(storedId);
        ImageStorageService service = new ImageStorageService(gridFsTemplate);

        BufferedImage image = new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.WHITE);
        graphics.drawString("Property photo", 100, 100);
        graphics.dispose();
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(image, "png", original);

        String id = service.store(
            new MockMultipartFile("imagenes", "house.png", "image/png", original.toByteArray()),
            "property-42", "Casa Niza", 2);

        ArgumentCaptor<InputStream> imageCaptor = ArgumentCaptor.forClass(InputStream.class);
        ArgumentCaptor<String> filenameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Document> metadataCaptor = ArgumentCaptor.forClass(Document.class);
        verify(gridFsTemplate).store(imageCaptor.capture(), filenameCaptor.capture(),
            contentTypeCaptor.capture(), metadataCaptor.capture());
        BufferedImage storedImage = ImageIO.read(imageCaptor.getValue());

        assertEquals(storedId.toHexString(), id);
        assertEquals("image/jpeg", contentTypeCaptor.getValue());
        assertTrue(filenameCaptor.getValue().contains("property-42-casa-niza-imagen-2"));
        Document metadata = metadataCaptor.getValue();
        assertEquals("property-42", metadata.getString("propertyId"));
        assertEquals("Casa Niza", metadata.getString("propertyTitle"));
        assertEquals(2, metadata.getInteger("imageNumber"));
        assertEquals("house.png", metadata.getString("originalFilename"));
        assertEquals(1600, storedImage.getWidth());
        assertEquals(800, storedImage.getHeight());
    }

    @Test
    void storesWebpWithoutChangingItsFormat() throws Exception {
        GridFsTemplate gridFsTemplate = org.mockito.Mockito.mock(GridFsTemplate.class);
        ObjectId storedId = new ObjectId();
        when(gridFsTemplate.store(any(InputStream.class), anyString(), anyString(), any())).thenReturn(storedId);
        ImageStorageService service = new ImageStorageService(gridFsTemplate);
        byte[] webpHeader = new byte[] { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P' };

        service.store(new MockMultipartFile("imagenes", "house.webp", "image/webp", webpHeader));

        ArgumentCaptor<InputStream> imageCaptor = ArgumentCaptor.forClass(InputStream.class);
        ArgumentCaptor<String> filenameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Document> metadataCaptor = ArgumentCaptor.forClass(Document.class);
        verify(gridFsTemplate).store(imageCaptor.capture(), filenameCaptor.capture(),
            contentTypeCaptor.capture(), metadataCaptor.capture());
        assertEquals("image/webp", contentTypeCaptor.getValue());
        assertEquals("WEBP", filenameCaptor.getValue().substring(filenameCaptor.getValue().length() - 4).toUpperCase());
        assertEquals(12, imageCaptor.getValue().readAllBytes().length);
    }
}