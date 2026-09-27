package com.example.inmobiliaria.Service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.bson.types.ObjectId;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;

@Service
public class ImageStorageService {

    private static final int MAX_DIMENSION = 1600;
    private static final long MAX_PIXELS = 20_000_000L;
    private static final float JPEG_QUALITY = 0.82f;

    private final GridFsTemplate gridFsTemplate;

    private final MongoClient mongoClient;

    @Value("${spring.data.mongodb.database:bienesraices}")
    private String databaseName;

    public ImageStorageService(GridFsTemplate gridFsTemplate) {
        this(gridFsTemplate, null);
    }

    @Autowired
    public ImageStorageService(@Qualifier("gridFsTemplate") GridFsTemplate gridFsTemplate,
            MongoClient mongoClient) {
        this.gridFsTemplate = gridFsTemplate;
        this.mongoClient = mongoClient;
    }

    public String store(MultipartFile upload) throws IOException {
        return store(upload, null, null, 0);
    }

    public String store(MultipartFile upload, String propertyId, String propertyTitle, int imageNumber)
            throws IOException {
        if (upload == null || upload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen está vacía.");
        }

        byte[] original = upload.getBytes();
        ImagePayload payload = optimize(original);
        String token = ObjectId.get().toHexString();
        String filename = buildFilename(propertyId, propertyTitle, imageNumber, token, payload.extension());
        Document metadata = new Document("_contentType", payload.contentType())
                .append("originalFilename", upload.getOriginalFilename());
        if (propertyId != null && !propertyId.isBlank()) {
            metadata.append("propertyId", propertyId)
                    .append("propertyTitle", propertyTitle)
                    .append("imageNumber", imageNumber);
        }

        ObjectId id = gridFsTemplate.store(new ByteArrayInputStream(payload.bytes()), filename,
                payload.contentType(), metadata);
        return id.toHexString();
    }

    private String buildFilename(String propertyId, String propertyTitle, int imageNumber,
            String token, String extension) {
        if (propertyId == null || propertyId.isBlank()) {
            return "imagen-" + token + extension;
        }
        return "propiedad-" + propertyId + "-" + slug(propertyTitle)
                + "-imagen-" + imageNumber + "-" + token + extension;
    }

    private String slug(String value) {
        if (value == null || value.isBlank()) return "sin-titulo";
        String slug = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "sin-titulo" : slug.substring(0, Math.min(slug.length(), 48));
    }

    public GridFSFile findById(String id) {
        if (!ObjectId.isValid(id)) {
            return null;
        }
        Query query = Query.query(Criteria.where("_id").is(new ObjectId(id)));
        GridFSFile file = gridFsTemplate.findOne(query);
        return file != null ? file : legacyBucket().find(Filters.eq("_id", new ObjectId(id))).first();
    }

    public Resource getResource(GridFSFile file) {
        Query query = Query.query(Criteria.where("_id").is(file.getObjectId()));
        if (gridFsTemplate.findOne(query) != null) {
            return gridFsTemplate.getResource(file);
        }
        return new InputStreamResource(legacyBucket().openDownloadStream(file.getObjectId()));
    }

    public void deleteByReference(String reference) {
        String prefix = "/api/imagenes/";
        if (reference == null || !reference.startsWith(prefix)) {
            return;
        }

        String id = reference.substring(prefix.length());
        if (ObjectId.isValid(id)) {
            Query query = Query.query(Criteria.where("_id").is(new ObjectId(id)));
            gridFsTemplate.delete(query);
            GridFSFile legacyFile = legacyBucket().find(Filters.eq("_id", new ObjectId(id))).first();
            if (legacyFile != null) {
                legacyBucket().delete(legacyFile.getObjectId());
            }
        }
    }

    private GridFSBucket legacyBucket() {
        return GridFSBuckets.create(mongoClient.getDatabase(databaseName), "fs");
    }

    private ImagePayload optimize(byte[] original) throws IOException {
        if (isWebp(original)) {
            return new ImagePayload(original, "image/webp", ".webp");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Formato no compatible. Usa JPG, PNG o WEBP.");
            }

            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Formato no compatible. Usa JPG, PNG o WEBP.");
                }

                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La resolución de la imagen es demasiado grande.");
                }

                return new ImagePayload(toOptimizedJpeg(reader.read(0)), "image/jpeg", ".jpg");
            } finally {
                reader.dispose();
            }
        }
    }

    private byte[] toOptimizedJpeg(BufferedImage source) throws IOException {
        double scale = Math.min(1.0, (double) MAX_DIMENSION / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No hay un codificador JPEG disponible.");
        }

        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(resized, null, null), parameters);
        } finally {
            writer.dispose();
            resized.flush();
            source.flush();
        }
        return bytes.toByteArray();
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private record ImagePayload(byte[] bytes, String contentType, String extension) {
    }
}