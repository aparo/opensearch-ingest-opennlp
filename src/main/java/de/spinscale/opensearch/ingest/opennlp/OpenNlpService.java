/*
 * Copyright [2016] [Alexander Reelsen]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package de.spinscale.opensearch.ingest.opennlp;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.Supplier;
import org.opensearch.OpenSearchException;
import org.opensearch.common.StopWatch;
import org.opensearch.common.Strings;
import org.opensearch.common.settings.Settings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenNLP name finders are not thread safe, so we load them via a thread local hack
 */
public class OpenNlpService {

    private static final Logger logger = LogManager.getLogger(OpenNlpService.class);
    private final Path configDirectory;
    private Settings settings;

    private ThreadLocal<TokenNameFinderModel> threadLocal = new ThreadLocal<>();
    private Map<String, TokenNameFinderModel> nameFinderModels = new ConcurrentHashMap<>();

    OpenNlpService(Path configDirectory, Settings settings) {
        this.configDirectory = configDirectory;
        this.settings = settings;
    }

    Set<String> getModels() {
        return IngestOpenNlpPlugin.MODEL_FILE_SETTINGS.getAsMap(settings).keySet();
    }

    protected OpenNlpService start() {
        StopWatch sw = new StopWatch("models-loading");
        Map<String, String> settingsMap = IngestOpenNlpPlugin.MODEL_FILE_SETTINGS.getAsMap(settings);
        for (Map.Entry<String, String> entry : settingsMap.entrySet()) {
            String name = entry.getKey();
            sw.start(name);
            Path path = configDirectory.resolve(entry.getValue());
            try (InputStream is = Files.newInputStream(path)) {
                nameFinderModels.put(name, new TokenNameFinderModel(is));
            } catch (IOException e) {
                logger.error((Supplier<?>) () -> new ParameterizedMessage("Could not load model [{}] with path [{}]", name, path), e);
            }
            sw.stop();
        }

        if (settingsMap.keySet().size() == 0) {
            logger.error("Did not load any models for ingest-opennlp plugin, none configured");
        } else {
            logger.info("Read models in [{}] for {}", sw.totalTime(), settingsMap.keySet());
        }

        return this;
    }

    public ExtractedEntities find(String content, String field) {
        try {
            if (!nameFinderModels.containsKey(field)) {
                throw new OpenSearchException("Could not find field [{}], possible values {}", field, nameFinderModels.keySet());
            }
            TokenNameFinderModel finderModel = nameFinderModels.get(field);
            if (threadLocal.get() == null || !threadLocal.get().equals(finderModel)) {
                threadLocal.set(finderModel);
            }

            String[] tokens = SimpleTokenizer.INSTANCE.tokenize(content);
            Span[] spans = new NameFinderME(finderModel).find(tokens);

            return new ExtractedEntities(tokens, spans);
        } finally {
            threadLocal.remove();
        }
    }

    static String createAnnotatedText(String content, List<ExtractedEntities> extractedEntities) {
        // these spans contain the real offset of each word in start/end variables!
        // the spans of the method argument contain the offset of each token, as mentioned in tokens!
        Span[] spansWithRealOffsets = SimpleTokenizer.INSTANCE.tokenizePos(content);

        List<Span> spansList = new ArrayList<>();
        extractedEntities.stream()
                .map(ExtractedEntities::getSpans)
                .forEach(s -> spansList.addAll(Arrays.asList(s)));

        Span[] spans = NameFinderME.dropOverlappingSpans(spansList.toArray(new Span[0]));
        String[] tokens = extractedEntities.get(0).getTokens();

        // shortcut if there is no enrichment to be done
        if (spans.length == 0) {
            return content;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            final int idx = i;
            String token = tokens[i];

            final Optional<Span> optionalSpan = Arrays.stream(spans).filter(s -> s.getStart() == idx).findFirst();
            if (optionalSpan.isPresent()) {
                Span span = optionalSpan.get();
                int start = span.getStart();
                int end = span.getEnd();
                String type = span.getType();

                String[] spanTokens = new String[end - start];
                int spanPosition = 0;
                for (int tokenPosition = start ; tokenPosition < end; tokenPosition++) {
                    spanTokens[spanPosition++] = tokens[tokenPosition];
                }
                String entityString = Strings.arrayToDelimitedString(spanTokens, " ");

                builder.append("[");
                builder.append(entityString);
                builder.append("](");
                builder.append(Strings.capitalize(type));
                builder.append("_");
                builder.append(entityString);
                builder.append(")");
                i = end - 1;
            } else {
                builder.append(token);
            }

            // only append a whitespace, if the offsets actually differ
            if (i < tokens.length - 1) {
                if (spansWithRealOffsets[i].getEnd() != spansWithRealOffsets[i+1].getStart()) {
                    builder.append(" ");
                }
            }
        }

        return builder.toString();
    }
}
