package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.ToString;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class CrptApi<T> {

    private final TimeUnit timeUnit;

    private final ExecutorService es;

    private final Semaphore semaphore;

    private final ObjectMapper objectMapper;

    public CrptApi(final int requestLimit, final TimeUnit timeIntervalMillis) {
        this.timeUnit = timeIntervalMillis;
        this.semaphore = new Semaphore(requestLimit);
        this.es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.objectMapper = new ObjectMapper();
    }

    public T createDocument(final Document document, final String signature) {
        try {
            semaphore.acquire();
            final var result = es.submit(() -> sendRequest(document, signature));
            Thread.sleep(getThreadSleepDuration());
            semaphore.release();
            return objectMapper.readValue(result.get(), new TypeReference<>() {
            });
        } catch (JsonMappingException e) {
            e.printStackTrace();
            return null;
        } catch (final Exception e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        }
    }

    private String sendRequest(Document document, String signature) throws InterruptedException {
        final var client = HttpClient.newHttpClient();
        try {
            return client.send(
                    buildRequest(document, signature),
                    HttpResponse.BodyHandlers.ofString()
            ).body();
        } catch (final IOException e) {
            throw new IllegalStateException("Request failed for document with id: %s".formatted(document.getDoc_id()));
        }
    }

    private long getThreadSleepDuration() {
        return TimeUnit.MILLISECONDS.convert(1, timeUnit);
    }


    private HttpRequest buildRequest(final Document document, final String signature) {
        final var documentJson = objectMapper.valueToTree(document);
        return HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(documentJson.toString()))
                .build();
    }

    public static void main(String[] args) {
        var crptApi = new CrptApi(5, TimeUnit.SECONDS);

        var document = new Document();
        var product = new Product();

        product.setCertificate_document("Certificate123");

        document.setProducts(List.of(product));

        document.setDoc_id("123");
        document.setDoc_status("Draft");

        crptApi.createDocument(document, "signature123");
    }

}

@Data
@ToString
class Document {

    private Description description;
    private String doc_id;
    private String doc_status;
    private String doc_type;
    private boolean importRequest;
    private String owner_inn;
    private String participant_inn;
    private String producer_inn;
    private String production_date;
    private String production_type;
    private List<Product> products;
    private String reg_date;
    private String reg_number;
}

@Data
@ToString
class Product {
    private String certificate_document;
    private String certificate_document_date;
    private String certificate_document_number;
    private String owner_inn;
    private String producer_inn;
    private String production_date;
    private String tnved_code;
    private String uit_code;
    private String uitu_code;

}

@Data
@ToString
class Description {
    private String participantInn;

    public String getParticipantInn() {
        return participantInn;
    }

    public void setParticipantInn(String participantInn) {
        this.participantInn = participantInn;
    }
}