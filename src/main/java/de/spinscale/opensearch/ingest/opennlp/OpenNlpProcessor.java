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

import org.opensearch.common.Strings;
import org.opensearch.ingest.AbstractProcessor;
import org.opensearch.ingest.IngestDocument;
import org.opensearch.ingest.Processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.opensearch.ingest.ConfigurationUtils.readOptionalList;
import static org.opensearch.ingest.ConfigurationUtils.readOptionalStringProperty;
import static org.opensearch.ingest.ConfigurationUtils.readStringProperty;

public class OpenNlpProcessor extends AbstractProcessor {

    static final String TYPE = "opennlp";

    private final OpenNlpService openNlpService;
    private final String sourceField;
    private final String targetField;
    private final String annotatedTextField;
    private final Set<String> fields;

    OpenNlpProcessor(OpenNlpService openNlpService, String tag, String sourceField, String targetField, String annotatedTextField,
                     Set<String> fields, String description) {
        super(tag, description);
        this.openNlpService = openNlpService;
        this.sourceField = sourceField;
        this.targetField = targetField;
        this.annotatedTextField = annotatedTextField;
        this.fields = fields;
    }

    @Override
    public IngestDocument execute(IngestDocument ingestDocument) {
        String content = ingestDocument.getFieldValue(sourceField, String.class);

        if (Strings.hasLength(content)) {
            Map<String, Set<String>> entities = new HashMap<>();
            mergeExisting(entities, ingestDocument, targetField);

            List<ExtractedEntities> extractedEntities = new ArrayList<>();
            for (String field : fields) {
                ExtractedEntities data = openNlpService.find(content, field);
                extractedEntities.add(data);
                merge(entities, field, data.getEntityValues());
            }

            // convert set to list, otherwise toXContent serialization in simulate pipeline fails
            Map<String, List<String>> entitiesToStore = new HashMap<>();
            Iterator<Map.Entry<String, Set<String>>> iterator = entities.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Set<String>> entry = iterator.next();
                entitiesToStore.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }

            ingestDocument.setFieldValue(targetField, entitiesToStore);

            if (Strings.hasLength(annotatedTextField) && extractedEntities.isEmpty() == false) {
                String annotatedText = OpenNlpService.createAnnotatedText(content, extractedEntities);
                ingestDocument.setFieldValue(annotatedTextField, annotatedText);
            }
        }

        return ingestDocument;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory {

        private OpenNlpService openNlpService;

        Factory(OpenNlpService openNlpService) {
            this.openNlpService = openNlpService;
        }

        @Override
        public OpenNlpProcessor create(Map<String, Processor.Factory> registry, String processorTag, String description,
                                       Map<String, Object> config) {
            String field = readStringProperty(TYPE, processorTag, config, "field");
            String targetField = readStringProperty(TYPE, processorTag, config, "target_field", "entities");
            String annotatedTextField = readOptionalStringProperty(TYPE, processorTag, config, "annotated_text_field");
            List<String> fields = readOptionalList(TYPE, processorTag, config, "fields");
            final Set<String> foundFields = fields == null || fields.size() == 0 ? openNlpService.getModels() : new HashSet<>(fields);
            return new OpenNlpProcessor(openNlpService, processorTag, field, targetField, annotatedTextField, foundFields, description);
        }
    }

    private static void mergeExisting(Map<String, Set<String>> entities, IngestDocument ingestDocument, String targetField) {
        if (ingestDocument.hasField(targetField)) {
            @SuppressWarnings("unchecked")
            Map<String, Set<String>> existing = ingestDocument.getFieldValue(targetField, Map.class);
            entities.putAll(existing);
        } else {
            ingestDocument.setFieldValue(targetField, entities);
        }
    }

    private static void merge(Map<String, Set<String>> map, String key, Set<String> values) {
        if (values.size() == 0) return;

        if (map.containsKey(key)) {
            values.addAll(map.get(key));
        }

        map.put(key, values);
    }
}
