package com.carpark.singapore.carpark;

import com.carpark.singapore.entities.CarParkEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Loads car park static data on startup: tries the live dataset first, falling back
 * to a bundled snapshot if the live fetch fails, then upserts into the database so
 * re-running (e.g. container restart) is safe and idempotent.
 */
@Component
public class CarParkStaticDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CarParkStaticDataLoader.class);

    private final CarParkStaticDataClient client;
    private final CarParkRepository repository;
    private final Resource fallbackResource;

    public CarParkStaticDataLoader(
            CarParkStaticDataClient client,
            CarParkRepository repository,
            @Value("${parking.static-data.fallback-resource}") Resource fallbackResource) {
        this.client = client;
        this.repository = repository;
        this.fallbackResource = fallbackResource;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<CarParkEntity> carParks = loadFromLiveSourceOrFallback();
        repository.saveAll(carParks);
        log.info("Loaded {} car parks into the database", carParks.size());
    }

    private List<CarParkEntity> loadFromLiveSourceOrFallback() throws IOException {
        Optional<String> liveCsv = client.fetchLatestCsv();
        if (liveCsv.isPresent()) {
            log.info("Using live car park static dataset");
            return CarParkCsvParser.parse(new StringReader(liveCsv.get()));
        }
        log.info("Using bundled fallback car park static dataset");
        try (Reader reader = new InputStreamReader(fallbackResource.getInputStream(), StandardCharsets.UTF_8)) {
            return CarParkCsvParser.parse(reader);
        }
    }
}
