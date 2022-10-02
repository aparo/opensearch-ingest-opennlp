# OpenSearch OpenNLP Ingest Processor

This is a port of [spinscale's ElasticSearch OpenNLP ingest plugin](https://github.com/spinscale/elasticsearch-ingest-opennlp). 
The code was migrate using my migration script [ElasticSearch to OpenSearch Migration Scripts](https://github.com/aparo/elasticsearch-opensearch-migration-scripts)

This processor is doing named/date/location/'whatever you have a model for' entity recognition and stores the output in the JSON before it is being stored.

This plugin is also intended to show you, that using gradle as a build system makes it very easy to reuse the testing facilities that opensearch already provides. First, you can run regular tests, but by adding a rest test, the plugin will be packaged and unzipped against opensearch, allowing you to execute a real end-to-end test, by just adding a java test class.

## Installation

| OS    | Command |
| ----- | ------- |
| 1.1.0  | `bin/opensearch-plugin install https://github.com/aparo/opensearch-ingest-opennlp/releases/download/1.1.0/ingest-opennlp-1.1.0.zip` |
| 1.2.0  | `bin/opensearch-plugin install https://github.com/aparo/opensearch-ingest-opennlp/releases/download/1.2.0/ingest-opennlp-1.2.0.zip` |
| 1.2.2  | `bin/opensearch-plugin install https://github.com/aparo/opensearch-ingest-opennlp/releases/download/1.2.2/ingest-opennlp-1.2.2.zip` |
| 1.2.3  | `bin/opensearch-plugin install https://github.com/aparo/opensearch-ingest-opennlp/releases/download/1.2.3/ingest-opennlp-1.2.3.zip` |
| 1.2.4  | `bin/opensearch-plugin install https://github.com/aparo/opensearch-ingest-opennlp/releases/download/1.2.4/ingest-opennlp-1.2.4.zip` |
| 1.3.0  | `bin/opensearch-plugin install https://github.com/aparo/opensearch-ingest-opennlp/releases/download/1.3.0/ingest-opennlp-1.3.0.zip` |
| 1.3.1  | `bin/opensearch-plugin install https://github.com/aparo/opensearch-ingest-opennlp/releases/download/1.3.1/ingest-opennlp-1.3.1.zip` |
| 1.3.2  | `bin/opensearch-plugin install https://github.com/aparo/opensearch-ingest-opennlp/releases/download/1.3.2/ingest-opennlp-1.3.2.zip` |
| 2.0.0  | `bin/opensearch-plugin install https://github.com/aparo/opensearch-ingest-opennlp/releases/download/2.0.0/ingest-opennlp-2.0.0.zip` |

**IMPORTANT**: If you are running this plugin with OpenSearch 1.1.0 or
newer, you need to download the NER models from sourceforge after
installation.

To download the models, run the following under Linux and osx (this is in
the `bin` directory of your OpenSearch installation)

```
bin/ingest-opennlp/download-models
```

If you are using windows, please use the following command

```
bin\ingest-opennlp\download-models.bat
```


## Usage

This is how you configure a pipeline with support for opennlp

You can add the following lines to the `config/opensearch.yml` (as those models are shipped by default, they are easy to enable). The models are looked up in the `config/ingest-opennlp/` directory.

```
ingest.opennlp.model.file.persons: en-ner-persons.bin
ingest.opennlp.model.file.dates: en-ner-dates.bin
ingest.opennlp.model.file.locations: en-ner-locations.bin
```

Now fire up OpenSearch and configure a pipeline

```
PUT _ingest/pipeline/opennlp-pipeline
{
  "description": "A pipeline to do named entity extraction",
  "processors": [
    {
      "opennlp" : {
        "field" : "my_field"
      }
    }
  ]
}

PUT /my-index/_doc/1?pipeline=opennlp-pipeline
{
  "my_field" : "Kobe Bryant was one of the best basketball players of all times. Not even Michael Jordan has ever scored 81 points in one game. Munich is really an awesome city, but New York is as well. Yesterday has been the hottest day of the year."
}

# response will contain an entities field with locations, dates and persons
GET /my-index/_doc/1
```

You can also specify only certain named entities in the processor, i.e. if you only want to extract persons


```
PUT _ingest/pipeline/opennlp-pipeline
{
  "description": "A pipeline to do named entity extraction",
  "processors": [
    {
      "opennlp" : {
        "field" : "my_field"
        "fields" : [ "persons" ]
      }
    }
  ]
}
```

You can also emit text in the format used by the [annotated text plugin](https://www.elastic.co/guide/en/opensearch/plugins/current/mapper-annotated-text.html).

```
PUT _ingest/pipeline/opennlp-pipeline
{
  "description": "A pipeline to do named entity extraction",
  "processors": [
    {
      "opennlp" : {
        "field" : "my_field",
        "annotated_text_field" : "my_annotated_text_field"
      }
    }
  ]
}
```

## Configuration

You can configure own models per field, the setting for this is prefixed `ingest.opennlp.model.file.`. So you can configure any model with any field name, by specifying a name and a path to file, like the three examples below:

| Parameter | Use |
| --- | --- |
| ingest.opennlp.model.file.names    | Configure the file for named entity recognition for the field name        |
| ingest.opennlp.model.file.dates    | Configure the file for date entity recognition for the field date         |
| ingest.opennlp.model.file.persons  | Configure the file for person entity recognition for the field person     |
| ingest.opennlp.model.file.WHATEVER | Configure the file for WHATEVER entity recognition for the field WHATEVER |

## Development setup & running tests

In order to install this plugin, you need to create a zip distribution first by running

```bash
./gradlew clean check
```

This will produce a zip file in `build/distributions`. As part of the build, the models are packaged into the zip file, but need to be downloaded before. There is a special task in the `build.gradle` which is downloading the models, in case they dont exist.

After building the zip file, you can install it like this

```bash
bin/plugin install file:///path/to/opensearch-ingest-opennlp/build/distribution/ingest-opennlp-X.Y.Z-SNAPSHOT.zip
```

Ensure that you have the models downloaded, before testing.

## Bugs & TODO

* A couple of groovy build mechanisms from core are disabled. See the `build.gradle` for further explanations
* Only the most basic NLP functions are exposed, please fork and add your own code to this!

