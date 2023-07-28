package dev.digitaldragon;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class Main {
    private static final int NUM_THREADS = 4;
    private static final int BATCH_SIZE = 1500; // Adjust the batch size as needed

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS);
        //List<String> filenames = new ArrayList<>();
        // Add your code to populate the filenames list here
        //String[] filenames = new String[] {"domains-003.txt", "domains-004.txt", "domains-005.txt", "domains-006.txt"};
        String[] filenames = new String[] {"domains.txt"};


        // Use a semaphore to control the number of open connections to MongoDB
        Semaphore semaphore = new Semaphore(NUM_THREADS);

        for (String filename : filenames) {
            executorService.submit(() -> {
                System.out.println(filename);
                try {
                    // Acquire a permit from the semaphore
                    semaphore.acquire();
                    try {
                        MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
                        MongoDatabase database = mongoClient.getDatabase("crawly");
                        MongoCollection<Document> processingCollection = database.getCollection("processing");

                        File file = new File(filename);
                        BufferedReader reader = new BufferedReader(new FileReader(file));
                        List<Document> batchDocuments = new ArrayList<>(BATCH_SIZE);
                        String line;
                        int i = 0;

                        while ((line = reader.readLine()) != null) {
                            i++;
                            String url = String.format("http://%s/", line);

                            Document document = new Document()
                                    .append("url", url)
                                    .append("domain", line)
                                    .append("queue_reason", "ZONE_DATA")
                                    .append("queued_by", "system")
                                    .append("queued_at", Instant.now());

                            batchDocuments.add(document);

                            // Insert the batch when it reaches the specified size
                            if (batchDocuments.size() >= BATCH_SIZE) {
                                processingCollection.insertMany(batchDocuments);
                                System.out.println("Flushed batch to mongodb! " + i);
                                batchDocuments.clear();
                            }
                        }

                        // Insert any remaining documents
                        if (!batchDocuments.isEmpty()) {
                            processingCollection.insertMany(batchDocuments);
                            System.out.println("Done! " + i);
                        }

                        reader.close();
                    } finally {
                        // Release the permit to allow another thread to use the connection
                        semaphore.release();
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

        executorService.shutdown();
    }
}
