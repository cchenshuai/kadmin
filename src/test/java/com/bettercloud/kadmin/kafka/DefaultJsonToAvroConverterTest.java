package com.bettercloud.kadmin.kafka;

import com.bettercloud.kadmin.api.kafka.AvrifyConverter;
import com.bettercloud.util.Opt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.NonNull;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

/**
 * Created by davidesposito on 7/8/16.
 */
public class DefaultJsonToAvroConverterTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private DefaultJsonToAvroConverter converter;

    @Before
    public void setup() {
        converter = new DefaultJsonToAvroConverter();
    }

    private String w(String s) {
        return '"' + s + '"';
    }

    private SchemaBuilder sb() {
        return sb(null);
    }

    private SchemaBuilder sb(String name) {
        return sb(name, null);
    }

    private SchemaBuilder sb(String name, String namespace) {
        return new SchemaBuilder(name, namespace);
    }

    private SchemaBuilder sb(String name, String namespace, String type) {
        return new SchemaBuilder(name, namespace)
                .type(type);
    }

    private JsonNode s(String name, String namespace, @NonNull String type) {
        return sb(name, namespace)
                .type(type)
                .build();
    }

    private JsonNode s(String name, String namespace, @NonNull JsonNode type) {
        return sb(name, namespace)
                .type(type)
                .build();
    }

    private JsonNode s(String name, String namespace, @NonNull List<String> types) {
        SchemaBuilder sb = sb(name, namespace);
        types.stream().forEach(t -> sb.type(t));
        return sb.build();
    }

    private JsonNodeBuilder b() {
        return new JsonNodeBuilder();
    }

    private JsonArrayNodeBuilder a() {
        return new JsonArrayNodeBuilder();
    }

    private Schema schema(String rawSchema) {
        return new Schema.Parser().parse(rawSchema);
    }

    @Test
    public void testSchemaRegEmptyPrimitive() {
        JsonNode schemaNode = s("test", null, "string");
        Schema schema = schema(schemaNode.toString());
        Map<String, Schema> registeredSchemas = Maps.newHashMap();
        converter.getRegisteredSchemas(schema, registeredSchemas);
        Assert.assertTrue("Not Empty: " + registeredSchemas, registeredSchemas.isEmpty());
    }

    @Test
    public void testSchemaRegRecord() {
        JsonNode schemaNode = sb("test", null, "record")
                .field()
                .build();
        testSchemaRegistration(schemaNode.toString(), Lists.newArrayList("test"));
    }

    @Test
    public void testSchemaRegRecordWns() {
        JsonNode schemaNode = sb("test", "foo.bar", "record")
                .field()
                .build();
        testSchemaRegistration(schemaNode.toString(), Lists.newArrayList("foo.bar.test"));
    }

    @Test
    public void testSchemaRegRecordWfields() {
        JsonNode schemaNode = sb("test", "foo.bar", "record")
                .field(s("s", null, "string"))
                .field(s("i", null, "int"))
                .build();
        testSchemaRegistration(schemaNode.toString(), Lists.newArrayList("foo.bar.test"));
    }

    @Test
    public void testSchemaRegRecordNested() {
        JsonNode schemaNode = sb("test", "foo.bar", "record")
                .field(s("s", null, "string"))
                .field(s("i", null, "int"))
                .field(sb("nested", null)
                        .type(sb("Nested", null, "record").field().build())
                        .build()
                )
                .build();
        testSchemaRegistration(schemaNode.toString(), Lists.newArrayList("foo.bar.test", "foo.bar.Nested"));
    }

    @Test
    public void testSchemaRegRecordNestedUnion() {
        JsonNode schemaNode = sb("test", "foo.bar", "record")
                .field(s("s", null, "string"))
                .field(s("i", null, "int"))
                .field(sb("nested", null)
                        .type("null")
                        .type("string")
                        .type(sb("Nested", null, "record").field().build())
                        .build()
                )
                .build();
        testSchemaRegistration(schemaNode.toString(), Lists.newArrayList("foo.bar.test", "foo.bar.Nested"));
    }

    @Test
    public void testSchemaRegRecordNestedWns() {
        JsonNode schemaNode = sb("test", "foo.bar", "record")
                .field(s("s", null, "string"))
                .field(s("i", null, "int"))
                .field(sb("nested", null)
                        .type(sb("Nested", "vim", "record").field().build())
                        .build()
                )
                .build();
        testSchemaRegistration(schemaNode.toString(), Lists.newArrayList("foo.bar.test", "vim.Nested"));
    }

    @Test
    public void testSchemaRegArray() {
        JsonNode schemaNode = sb("test", "foo.bar", "record")
                .field(
                        sb("arr")
                                .type(
                                        sb()
                                                .type("array")
                                                .item("string")
                                                .build()
                                )
                                .build()
                )
                .build();
        testSchemaRegistration(schemaNode.toString(), Lists.newArrayList("foo.bar.test"));
    }

    @Test
    public void testSchemaRegArrayNested() {
        JsonNode schemaNode = sb("test", "foo.bar", "record")
                .field(
                        sb("arr")
                                .type(
                                        sb()
                                                .type("array")
                                                .item(
                                                        sb("Nested", null, "record")
                                                                .field()
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();
        testSchemaRegistration(schemaNode.toString(), Lists.newArrayList("foo.bar.test", "foo.bar.Nested"));
    }

    @Test
    public void testSchemaRegArrayNestedUnion() {
        JsonNode schemaNode = sb("test", "foo.bar", "record")
                .field(
                        sb("arr")
                                .type(
                                        sb()
                                                .type("array")
                                                .item("null")
                                                .item("string")
                                                .item(
                                                        sb("Nested", null, "record")
                                                                .field()
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();
        testSchemaRegistration(schemaNode.toString(), Lists.newArrayList("foo.bar.test", "foo.bar.Nested"));
    }

    private void testSchemaRegistration(String rawSchema, List<String> expectedKeys) {
        Schema schema = schema(rawSchema);
        Map<String, Schema> registeredSchemas = Maps.newHashMap();
        converter.getRegisteredSchemas(schema, registeredSchemas);
        Assert.assertFalse("Was empty", registeredSchemas.isEmpty());
        Assert.assertEquals("Wrong number of schemas registered", expectedKeys.size(), registeredSchemas.size());
        expectedKeys.stream().forEach(expectedKey -> {
            Assert.assertTrue("Expected key: " + expectedKey + " but found " + registeredSchemas.keySet(), registeredSchemas.containsKey(expectedKey));
            Assert.assertNotNull("Expected value for key: " + expectedKey, registeredSchemas.get(expectedKey));
        });
    }

    @Test
    public void testString() {
        test(w("test"), w("string"));
    }

    @Test
    public void testInt() {
        test("123", w("int"));
    }

    @Test
    public void testLong() {
        test("123", w("long"));
    }

    @Test
    public void testFloat() {
        test("123.456", w("float"));
    }

    @Test
    public void testDouble() {
        test("123.456", w("double"));
    }

    @Test
    public void testSimple() {
        JsonNode json = b().putString("name", "David")
                .putInt("age", 100)
                .build();

        JsonNode schema = sb("Person", "foo", "record")
                .field(s("name", null, "string"))
                .field(s("age", null, "int"))
                .build();

        test(json.toString(), schema.toString());
    }

    @Test
    public void testUnion() {
        JsonNode json = b().putString("name", "David")
                .putInt("age", 100)
                .build();

        JsonNode expected = b().putNode(
                        "name", b().putString("string", "David").build()
                ).putInt("age", 100)
                .build();

        JsonNode schema = sb("Person", "foo", "record")
                .field(s("name", null, Lists.newArrayList("string", "null")))
                .field(s("age", null, "int"))
                .build();

        test(json.toString(), schema.toString(), expected.toString());
    }

    @Test
    public void testNestedUnion() {
        JsonNode json = b().putString("name", "David")
                .putInt("age", 100)
                .putNode(
                        "job", b().putString("title", "Developer")
                                .putInt("salary", 1000)
                                .putBoolean("active", true)
                                .build()
                )
                .build();

        JsonNode expected = b()
                .putNode(
                        "name", b().putString("string", "David").build()
                )
                .putInt("age", 100)
                .putNode(
                        "job", b().putString("title", "Developer")
                                .putNode(
                                        "salary", b().putInt("int", 1000).build()
                                )
                                .putNode(
                                        "active", b().putBoolean("boolean", true).build()
                                )
                                .build()
                )
                .build();

        JsonNode schema = sb("Person", "foo", "record")
                .field(s("name", null, Lists.newArrayList("string", "null")))
                .field(s("age", null, "int"))
                .field(
                        sb("job")
                                .type(
                                        sb("Job", "foo", "record")
                                                .field(s("title", null, "string"))
                                                .field(s("salary", null, Lists.newArrayList("int", "float", "double")))
                                                .field(s("active", null, Lists.newArrayList("boolean", "null")))
                                                .build()
                                )
                                .build()
                )
                .build();

        test(json.toString(), schema.toString(), expected.toString());
    }

    @Test
    public void testRecursion() {
        JsonNode json = b().putString("name", "David")
                .putNode(
                        "manager", b().putString("name", "John").build()
                )
                .build();

        JsonNode schema = sb("Person", "bar", "record")
                .field(s("manager", null, "bar.Person"))
                .field(s("name", null, "string"))
                .build();

        test(json.toString(), schema.toString());
    }

    @Test
    public void testDeepRecursion() {
        JsonNode json = b().putString("name", "David")
                .putNode(
                        "manager", b().putString("name", "John")
                                .putNode(
                                        "manager", b().putString("name", "Kevin")
                                                .putNode(
                                                        "manager", b().putString("name", "Hardwick").build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        JsonNode schema = sb("Person", "bar", "record")
                .field(s("manager", null, "bar.Person"))
                .field(s("name", null, "string"))
                .build();

        test(json.toString(), schema.toString());
    }

    @Test
    public void testRecursiveUnion() {
        JsonNode json = b().putString("name", "David")
                .putNode(
                        "manager", b().putString("name", "John").build()
                )
                .build();

        JsonNode expected = b().putString("name", "David")
                .putNode(
                        "manager", b().putNode(
                                "bar.Person", b().putString("name", "John").build()
                        )
                        .build()
                )
                .build();

        JsonNode schema = sb("Person", "bar", "record")
                .field(s("manager", null, Lists.newArrayList("bar.Person", "null")))
                .field(s("name", null, "string"))
                .build();

        test(json.toString(), schema.toString(), expected.toString());
    }

    @Test
    public void testDeepRecursiveUnion() {
        JsonNode json = b().putString("name", "David")
                .putNode(
                        "manager", b().putString("name", "John")
                                .putNode(
                                        "manager", b().putString("name", "Kevin").build()
                                )
                                .build()
                )
                .build();

        JsonNode expected = b().putString("name", "David")
                .putNode(
                        "manager", b().putNode(
                                "bar.Person", b().putString("name", "John")
                                        .putNode(
                                                "manager", b().putNode(
                                                        "bar.Person", b().putString("name", "Kevin").build()
                                                )
                                                .build()
                                        )
                                        .build()
                        )
                                .build()
                )
                .build();

        JsonNode schema = sb("Person", "bar", "record")
                .field(s("name", null, "string"))
                .field(s("manager", null, Lists.newArrayList("bar.Person", "null")))
                .build();

        test(json.toString(), schema.toString(), expected.toString());
    }

    @Test
    public void testEventCall() throws IOException {
        JsonNode json = mapper.readTree(
                DefaultJsonToAvroConverterTest.class.getResource("/test/avro/EventCall.01.json")
        );

        JsonNode expected = mapper.readTree(
                DefaultJsonToAvroConverterTest.class.getResourceAsStream("/test/avro/EventCall.01.expected.json")
        );

        JsonNode schema = mapper.readTree(
                DefaultJsonToAvroConverterTest.class.getResourceAsStream("/test/avro/EventCall.schema.json")
        );

        test(json.toString(), schema.toString(), expected.toString());
    }
//
//    @Test
//    @Ignore
//    public void testMC() {
//        JsonNode cat = b().putString("sound", "catSound").build();
//        JsonNode json = b()
//                .putString("name2", "FIRST_MESSAGE")
//                .putString("role", "Role")
//                .putString("address", "Address")
//                .putInt("age", 21)
//                .putString("email", "Email")
//                .putString("location", "Location")
//                .putNode("nested", cat)
//                .putNode(
//                        "arr", a()
//                                .putNode(cat)
//                                .putInt(25)
//                                .build()
//                )
//                .build();
//
//        JsonNode expected = b()
//                .putString("name2", "FIRST_MESSAGE")
//                .putNode(
//                        "role", b().putString("string", "Role").build()
//                )
//                .putNode(
//                        "address", b().putString("string", "Address").build()
//                )
//                .putInt("age", 21)
//                .putString("email", "Email")
//                .putString("location", "Location")
//                .putNode(
//                        "nested", b().putNode("com.bettercloud.Cat", cat).build()
//                )
//                .putNode(
//                        "arr", a()
//                                .putNode(
//                                        b().putNode("com.bettercloud.Cat", cat).build()
//                                )
//                                .putNode(
//                                        b().putInt("int", 25).build()
//                                )
//                                .build()
//                )
//                .build();
//
//        JsonNode schema = sb("User", "com.bettercloud", "record")
//                .field(s("name2", null, "string"))
//                .field(s("age", null, "int"))
//                .field(s("address", null, Lists.newArrayList("null", "string")))
//                .field(s("role", null, Lists.newArrayList("null", "string")))
//                .field(s("email", null, "string"))
//                .field(s("location", null, "string"))
//                .field(
//                        sb("nested", null)
//                                .type("null")
//                                .type("string")
//                                .type(
//                                        sb("Cat", "com.bettercloud", "record")
//                                        .build()
//                                )
//                                .build()
//                )
//                .field(
//                        sb("arr", null)
//                                .item("int")
//                                .item("string")
//                                .item("com.bettercloud.Cat")
//                                .build()
//                )
//                .build();
//
//        test(json.toString(), schema.toString(), expected.toString());
//    }

    public void test(String json, String schema) {
        test(json, schema, json);
    }

    public void test(String json, String schema, String expected) {
        Object actual = Opt.of(converter.avrify(json, schema)).map(o -> o.toString()).orElse("NULL => NOT CONVERTED");
        Assert.assertEquals(schema, expected, actual);
    }

    private static class SchemaBuilder {

        private final ObjectNode node = mapper.createObjectNode();
        private final ArrayNode fields = mapper.createArrayNode();
        private ArrayNode type = mapper.createArrayNode();
        private ArrayNode items = mapper.createArrayNode();

        public SchemaBuilder(String name, String namespace) {
            Opt.of(name).ifPresent(n -> node.put("name", n));
            Opt.of(namespace).ifPresent(ns -> node.put("namespace", namespace));
        }

        JsonNode build() {
            if (this.type.size() == 1) {
                node.replace("type", this.type.get(0));
            } else if (this.type.size() > 1) {
                node.replace("type", this.type);
            }
            if (this.items.size() == 1) {
                node.replace("items", this.items.get(0));
            } else if (this.items.size() > 1) {
                node.replace("items", this.items);
            }
            return node;
        }

        public SchemaBuilder name(String name) {
            this.node.put("name", name);
            return this;
        }

        public SchemaBuilder nameSpace(String namespace) {
            this.node.put("namespace", namespace);
            return this;
        }

        public SchemaBuilder field() {
            this.node.replace("fields", this.fields);
            return this;
        }

        public SchemaBuilder field(JsonNode field) {
            this.node.replace("fields", this.fields);
            fields.add(field);
            return this;
        }

        public SchemaBuilder type(JsonNode type) {
            this.type.add(type);
            return this;
        }

        public SchemaBuilder type(String type) {
            this.type.add(type);
            return this;
        }

        public SchemaBuilder item(JsonNode type) {
            this.items.add(type);
            return this;
        }

        public SchemaBuilder item(String type) {
            this.items.add(type);
            return this;
        }
    }

    private static class JsonNodeBuilder {

        private final ObjectNode node = mapper.createObjectNode();

        JsonNode build() {
            return node;
        }

        JsonNodeBuilder putString(String key, String v) {
            node.put(key, v);
            return this;
        }

        JsonNodeBuilder putInt(String key, int v) {
            node.put(key, v);
            return this;
        }

        JsonNodeBuilder putLong(String key, long v) {
            node.put(key, v);
            return this;
        }

        JsonNodeBuilder putFloat(String key, float v) {
            node.put(key, v);
            return this;
        }

        JsonNodeBuilder putDouble(String key, double v) {
            node.put(key, v);
            return this;
        }

        JsonNodeBuilder putBoolean(String key, boolean v) {
            node.put(key, v);
            return this;
        }

        JsonNodeBuilder putNull(String key) {
            node.putNull(key);
            return this;
        }

        JsonNodeBuilder putNode(String key, JsonNode v) {
            node.replace(key, v);
            return this;
        }
    }

    private static class JsonArrayNodeBuilder {

        private final ArrayNode node = mapper.createArrayNode();

        JsonNode build() {
            return node;
        }

        JsonArrayNodeBuilder putString(String v) {
            node.add(v);
            return this;
        }

        JsonArrayNodeBuilder putInt(int v) {
            node.add(v);
            return this;
        }

        JsonArrayNodeBuilder putLong(long v) {
            node.add(v);
            return this;
        }

        JsonArrayNodeBuilder putFloat(float v) {
            node.add(v);
            return this;
        }

        JsonArrayNodeBuilder putDouble(double v) {
            node.add(v);
            return this;
        }

        JsonArrayNodeBuilder putBoolean(boolean v) {
            node.add(v);
            return this;
        }

        JsonArrayNodeBuilder putNull() {
            node.addNull();
            return this;
        }

        JsonArrayNodeBuilder putNode(JsonNode v) {
            node.add(v);
            return this;
        }
    }
}
