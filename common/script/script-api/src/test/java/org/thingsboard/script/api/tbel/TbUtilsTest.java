/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
 */
package org.thingsboard.script.api.tbel;

import com.google.common.collect.Lists;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mvel2.ExecutionContext;
import org.mvel2.ParserContext;
import org.mvel2.SandboxedParserConfiguration;
import org.mvel2.execution.ExecutionArrayList;
import org.mvel2.execution.ExecutionHashMap;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static java.lang.Character.MAX_RADIX;
import static java.lang.Character.MIN_RADIX;

@Slf4j
public class TbUtilsTest {

    private ExecutionContext ctx;

    private final float floatVal = 29.29824f;

    private final float floatValRev = -5.948442E7f;

    private final long longVal = 0x409B04B10CB295EAL;
    private final String longValHex = "409B04B10CB295EA";
    private final String longValHexRev = "EA95B20CB1049B40";

    private final double doubleVal = 1729.1729;
    private final double doubleValRev = -2.7208640774822924E205;


    @BeforeEach
    public void before() {
        SandboxedParserConfiguration parserConfig = ParserContext.enableSandboxedMode();
        parserConfig.addImport("JSON", TbJson.class);
        parserConfig.registerDataType("Date", TbDate.class, date -> 8L);
        parserConfig.registerDataType("Random", Random.class, date -> 8L);
        parserConfig.registerDataType("Calendar", Calendar.class, date -> 8L);
        try {
            TbUtils.register(parserConfig);
        } catch (Exception e) {
            log.error("Cannot register functions", e);
        }
        ctx = new ExecutionContext(parserConfig);
        Assertions.assertNotNull(ctx);
    }

    @AfterEach
    public void after() {
        ctx.stop();
    }

    @Test
    public void parseHexToInt() {
        Assertions.assertEquals(0xAB, TbUtils.parseHexToInt("AB"));

        Assertions.assertEquals(0xABBA, TbUtils.parseHexToInt("ABBA", true));
        Assertions.assertEquals(0xBAAB, TbUtils.parseHexToInt("ABBA", false));
        Assertions.assertEquals(0xAABBCC, TbUtils.parseHexToInt("AABBCC", true));
        Assertions.assertEquals(0xAABBCC, TbUtils.parseHexToInt("CCBBAA", false));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseHexToInt("AABBCCDD", true));
        Assertions.assertEquals(0x11BBCC22, TbUtils.parseHexToInt("11BBCC22", true));
        Assertions.assertEquals(0x11BBCC22, TbUtils.parseHexToInt("22CCBB11", false));
    }

    @Test
    public void parseBytesToInt_checkPrimitives() {
        int expected = 257;
        byte[] data = ByteBuffer.allocate(4).putInt(expected).array();
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4));
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 2, 2, true));
        Assertions.assertEquals(1, TbUtils.parseBytesToInt(data, 3, 1, true));

        expected = Integer.MAX_VALUE;
        data = ByteBuffer.allocate(4).putInt(expected).array();
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, true));

        expected = 0xAABBCCDD;
        data = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, true));
        data = new byte[]{(byte) 0xDD, (byte) 0xCC, (byte) 0xBB, (byte) 0xAA};
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, false));

        expected = 0xAABBCC;
        data = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC};
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 3, true));
        data = new byte[]{(byte) 0xCC, (byte) 0xBB, (byte) 0xAA};
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 3, false));
    }

    @Test
    public void parseBytesToInt_checkLists() {
        int expected = 257;
        List<Byte> data = toList(ByteBuffer.allocate(4).putInt(expected).array());
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4));
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 2, 2, true));
        Assertions.assertEquals(1, TbUtils.parseBytesToInt(data, 3, 1, true));

        expected = Integer.MAX_VALUE;
        data = toList(ByteBuffer.allocate(4).putInt(expected).array());
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, true));

        expected = 0xAABBCCDD;
        data = toList(new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD});
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, true));
        data = toList(new byte[]{(byte) 0xDD, (byte) 0xCC, (byte) 0xBB, (byte) 0xAA});
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, false));

        expected = 0xAABBCC;
        data = toList(new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC});
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 3, true));
        data = toList(new byte[]{(byte) 0xCC, (byte) 0xBB, (byte) 0xAA});
        Assertions.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 3, false));
    }

    @Test
    public void toFlatMap() {
        ExecutionHashMap<String, Object> inputMap = new ExecutionHashMap<>(16, ctx);
        inputMap.put("name", "Alice");
        inputMap.put("age", 30);
        inputMap.put("devices", new ExecutionArrayList<>(List.of(
                new ExecutionHashMap<>(16, ctx) {{
                    put("id", "dev001");
                    put("type", "sensor");
                }},
                new ExecutionHashMap<>(16, ctx) {{
                    put("id", "dev002");
                    put("type", "actuator");
                }}
        ), ctx));
        inputMap.put("settings", new ExecutionHashMap<>(16, ctx) {{
            put("notifications", true);
            put("timezone", "UTC-5");
            put("params", new ExecutionHashMap<>(16, ctx) {{
                put("param1", "value1");
                put("param2", "value2");
                put("param3", new ExecutionHashMap<>(16, ctx) {{
                    put("subParam1", "value1");
                    put("subParam2", "value2");
                }});
            }});
        }});
        ExecutionArrayList<String> excludeList = new ExecutionArrayList<>(ctx);
        excludeList.addAll(List.of("age", "id", "param1", "subParam2"));

        ExecutionHashMap<String, Object> expectedMapWithPath = new ExecutionHashMap<>(16, ctx);
        expectedMapWithPath.put("name", "Alice");
        expectedMapWithPath.put("devices.0.type", "sensor");
        expectedMapWithPath.put("devices.1.type", "actuator");
        expectedMapWithPath.put("settings.notifications", true);
        expectedMapWithPath.put("settings.timezone", "UTC-5");
        expectedMapWithPath.put("settings.params.param2", "value2");
        expectedMapWithPath.put("settings.params.param3.subParam1", "value1");

        ExecutionHashMap<String, Object> actualMapWithPaths = TbUtils.toFlatMap(ctx, inputMap, excludeList, true);

        Assertions.assertEquals(expectedMapWithPath, actualMapWithPaths);

        ExecutionHashMap<String, Object> expectedMapWithoutPaths = new ExecutionHashMap<>(16, ctx);
        expectedMapWithoutPaths.put("timezone", "UTC-5");
        expectedMapWithoutPaths.put("name", "Alice");
        expectedMapWithoutPaths.put("id", "dev002");
        expectedMapWithoutPaths.put("subParam2", "value2");
        expectedMapWithoutPaths.put("type", "actuator");
        expectedMapWithoutPaths.put("subParam1", "value1");
        expectedMapWithoutPaths.put("param1", "value1");
        expectedMapWithoutPaths.put("notifications", true);
        expectedMapWithoutPaths.put("age", 30);
        expectedMapWithoutPaths.put("param2", "value2");

        ExecutionHashMap<String, Object> actualMapWithoutPaths = TbUtils.toFlatMap(ctx, inputMap, false);

        Assertions.assertEquals(expectedMapWithoutPaths, actualMapWithoutPaths);
    }

    @Test
    public void parseInt() {
        Assertions.assertNull(TbUtils.parseInt(null));
        Assertions.assertNull(TbUtils.parseInt(""));
        Assertions.assertNull(TbUtils.parseInt(" "));

        Assertions.assertEquals((Integer) 0, TbUtils.parseInt("0"));
        Assertions.assertEquals((Integer) 0, TbUtils.parseInt("-0"));
        Assertions.assertEquals(java.util.Optional.of(473).get(), TbUtils.parseInt("473"));
        Assertions.assertEquals(java.util.Optional.of(-255).get(), TbUtils.parseInt("-0xFF"));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseInt("-0xFF123"));
        Assertions.assertEquals(java.util.Optional.of(-255).get(), TbUtils.parseInt("-FF"));
        Assertions.assertEquals(java.util.Optional.of(255).get(), TbUtils.parseInt("FF"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> TbUtils.parseInt("FFF"));
        Assertions.assertEquals(java.util.Optional.of(-2578).get(), TbUtils.parseInt("-0A12"));
        Assertions.assertEquals(java.util.Optional.of(-2578).get(), TbUtils.parseHexToInt("-0A12"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> TbUtils.parseHexToInt("A12", false));
        Assertions.assertEquals(java.util.Optional.of(-14866).get(), TbUtils.parseBigEndianHexToInt("-3A12"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> TbUtils.parseLittleEndianHexToInt("-A12"));

        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseInt("0xFG"));

        Assertions.assertEquals(java.util.Optional.of(102).get(), TbUtils.parseInt("1100110", 2));
        Assertions.assertEquals(java.util.Optional.of(-102).get(), TbUtils.parseInt("1111111111111111111111111111111111111111111111111111111110011010", 2));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseInt("1100210", 2));
        Assertions.assertEquals(java.util.Optional.of(13158).get(), TbUtils.parseInt("11001101100110", 2));
        Assertions.assertEquals(java.util.Optional.of(-13158).get(), TbUtils.parseInt("1111111111111111111111111111111111111111111111111100110010011010", 2));


        Assertions.assertEquals(java.util.Optional.of(63).get(), TbUtils.parseInt("77", 8));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseInt("18", 8));

        Assertions.assertEquals(java.util.Optional.of(-255).get(), TbUtils.parseInt("-FF", 16));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseInt("FG", 16));

        Assertions.assertEquals(java.util.Optional.of(Integer.MAX_VALUE).get(), TbUtils.parseInt(Integer.toString(Integer.MAX_VALUE), 10));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseInt(BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.valueOf(1)).toString(10), 10));
        Assertions.assertEquals(java.util.Optional.of(Integer.MIN_VALUE).get(), TbUtils.parseInt(Integer.toString(Integer.MIN_VALUE), 10));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseInt(BigInteger.valueOf(Integer.MIN_VALUE).subtract(BigInteger.valueOf(1)).toString(10), 10));

        Assertions.assertEquals(java.util.Optional.of(506070563).get(), TbUtils.parseInt("KonaIn", 30));

        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseInt("KonaIn", 10));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseInt(".456", 10));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseInt("4562.", 10));

        Assertions.assertThrows(IllegalArgumentException.class, () -> TbUtils.parseInt("KonaIn", MAX_RADIX+1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> TbUtils.parseInt("KonaIn", MIN_RADIX-1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> TbUtils.parseInt("KonaIn", 12));
    }

    @Test
    public void parseFloat() {
        String floatValStr = "29.29824";
        Assertions.assertEquals(java.util.Optional.of(floatVal).get(), TbUtils.parseFloat(floatValStr));
        String floatValHex = "41EA62CC";
        Assertions.assertEquals(0, Float.compare(floatVal, TbUtils.parseHexToFloat(floatValHex)));
        Assertions.assertEquals(0, Float.compare(floatValRev, TbUtils.parseHexToFloat(floatValHex, false)));
        Assertions.assertEquals(0, Float.compare(floatVal, TbUtils.parseBigEndianHexToFloat(floatValHex)));
        String floatValHexRev = "CC62EA41";
        Assertions.assertEquals(0, Float.compare(floatVal, TbUtils.parseLittleEndianHexToFloat(floatValHexRev)));
    }

    @Test
    public void toFixedFloat() {
        float actualF = TbUtils.toFixed(floatVal, 3);
        Assertions.assertEquals(1, Float.compare(floatVal, actualF));
        Assertions.assertEquals(0, Float.compare(29.298f, actualF));
    }

    @Test
    public void arseBytesToFloat() {
        byte[] floatValByte = {65, -22, 98, -52};
        Assertions.assertEquals(0, Float.compare(floatVal, TbUtils.parseBytesToFloat(floatValByte, 0)));
        Assertions.assertEquals(0, Float.compare(floatValRev, TbUtils.parseBytesToFloat(floatValByte, 0, false)));

        List<Byte> floatVaList = Bytes.asList(floatValByte);
        Assertions.assertEquals(0, Float.compare(floatVal, TbUtils.parseBytesToFloat(floatVaList, 0)));
        Assertions.assertEquals(0, Float.compare(floatValRev, TbUtils.parseBytesToFloat(floatVaList, 0, false)));
    }

    @Test
    public void parseLong() {
        Assertions.assertNull(TbUtils.parseLong(null));
        Assertions.assertNull(TbUtils.parseLong(""));
        Assertions.assertNull(TbUtils.parseLong(" "));

        Assertions.assertEquals((Long) 0L, TbUtils.parseLong("0"));
        Assertions.assertEquals((Long) 0L, TbUtils.parseLong("-0"));
        Assertions.assertEquals(java.util.Optional.of(473L).get(), TbUtils.parseLong("473"));
        Assertions.assertEquals(java.util.Optional.of(-65535L).get(), TbUtils.parseLong("-0xFFFF"));
        Assertions.assertEquals(java.util.Optional.of(4294967295L).get(), TbUtils.parseLong("FFFFFFFF"));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseLong("0xFGFFFFFF"));

        Assertions.assertEquals(java.util.Optional.of(13158L).get(), TbUtils.parseLong("11001101100110", 2));
        Assertions.assertEquals(java.util.Optional.of(-13158L).get(), TbUtils.parseLong("1111111111111111111111111111111111111111111111111100110010011010", 2));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseLong("11001101100210", 2));

        Assertions.assertEquals(java.util.Optional.of(9223372036854775807L).get(), TbUtils.parseLong("777777777777777777777", 8));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseLong("1787", 8));

        Assertions.assertEquals(java.util.Optional.of(-255L).get(), TbUtils.parseLong("-FF", 16));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseLong("FG", 16));


        Assertions.assertEquals(java.util.Optional.of(Long.MAX_VALUE).get(), TbUtils.parseLong(Long.toString(Long.MAX_VALUE), 10));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseLong(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(1)).toString(10), 10));
        Assertions.assertEquals(java.util.Optional.of(Long.MIN_VALUE).get(), TbUtils.parseLong(Long.toString(Long.MIN_VALUE), 10));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseLong(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.valueOf(1)).toString(10), 10));

        Assertions.assertEquals(java.util.Optional.of(218840926543L).get(), TbUtils.parseLong("KonaLong", 27));
        Assertions.assertThrows(NumberFormatException.class, () -> TbUtils.parseLong("KonaLong", 10));

        Assertions.assertEquals(longVal, TbUtils.parseHexToLong(longValHex));
        Assertions.assertEquals(longVal, TbUtils.parseHexToLong(longValHexRev, false));
        Assertions.assertEquals(longVal, TbUtils.parseBigEndianHexToLong(longValHex));
        Assertions.assertEquals(longVal, TbUtils.parseLittleEndianHexToLong(longValHexRev));
    }

    @Test
    public void parseBytesToLong() {
        byte[] longValByte = {64, -101, 4, -79, 12, -78, -107, -22};
        Assertions.assertEquals(longVal, TbUtils.parseBytesToLong(longValByte, 0, 8));
        Bytes.reverse(longValByte);
        Assertions.assertEquals(longVal, TbUtils.parseBytesToLong(longValByte, 0, 8, false));

        List<Byte> longVaList = Bytes.asList(longValByte);
        Assertions.assertEquals(longVal, TbUtils.parseBytesToLong(longVaList, 0, 8, false));
        long longValRev = 0xEA95B20CB1049B40L;
        Assertions.assertEquals(longValRev, TbUtils.parseBytesToLong(longVaList, 0, 8));
    }

    @Test
    public void parsDouble() {
        String doubleValStr = "1729.1729";
        Assertions.assertEquals(java.util.Optional.of(doubleVal).get(), TbUtils.parseDouble(doubleValStr));
        Assertions.assertEquals(0, Double.compare(doubleVal, TbUtils.parseHexToDouble(longValHex)));
        Assertions.assertEquals(0, Double.compare(doubleValRev, TbUtils.parseHexToDouble(longValHex, false)));
        Assertions.assertEquals(0, Double.compare(doubleVal, TbUtils.parseBigEndianHexToDouble(longValHex)));
        Assertions.assertEquals(0, Double.compare(doubleVal, TbUtils.parseLittleEndianHexToDouble(longValHexRev)));
    }

    @Test
    public void toFixedDouble() {
        double actualD = TbUtils.toFixed(doubleVal, 3);
        Assertions.assertEquals(-1, Double.compare(doubleVal, actualD));
        Assertions.assertEquals(0, Double.compare(1729.173, actualD));
    }

    @Test
    public void parseBytesToDouble() {
        byte[] doubleValByte = {64, -101, 4, -79, 12, -78, -107, -22};
        Assertions.assertEquals(0, Double.compare(doubleVal, TbUtils.parseBytesToDouble(doubleValByte, 0)));
        Assertions.assertEquals(0, Double.compare(doubleValRev, TbUtils.parseBytesToDouble(doubleValByte, 0, false)));

        List<Byte> doubleVaList = Bytes.asList(doubleValByte);
        Assertions.assertEquals(0, Double.compare(doubleVal, TbUtils.parseBytesToDouble(doubleVaList, 0)));
        Assertions.assertEquals(0, Double.compare(doubleValRev, TbUtils.parseBytesToDouble(doubleVaList, 0, false)));
    }

    @Test
    public void parseBytesDecodeToJson() throws IOException, IllegalAccessException {
        String expectedStr = "{\"hello\": \"world\"}";
        ExecutionHashMap<String, Object> expectedJson = new ExecutionHashMap<>(1, ctx);
        expectedJson.put("hello", "world");
        List<Byte> expectedBytes = TbUtils.stringToBytes(ctx, expectedStr);
        Object actualJson = TbUtils.decodeToJson(ctx, expectedBytes);
        Assertions.assertEquals(expectedJson, actualJson);
    }

    @Test
    public void parseStringDecodeToJson() throws IOException {
        String expectedStr = "{\"hello\": \"world\"}";
        ExecutionHashMap<String, Object> expectedJson = new ExecutionHashMap<>(1, ctx);
        expectedJson.put("hello", "world");
        Object actualJson = TbUtils.decodeToJson(ctx, expectedStr);
        Assertions.assertEquals(expectedJson, actualJson);
    }

    @Test
    public void stringToBytesInputObjectTest() throws IOException, IllegalAccessException {
        String expectedStr = "{\"hello\": \"world\"}";
        Object inputJson = TbUtils.decodeToJson(ctx, expectedStr);
        byte[] arrayBytes = {119, 111, 114, 108, 100};
        List<Byte> expectedBytes = Bytes.asList(arrayBytes);
        List<Byte> actualBytes = TbUtils.stringToBytes(ctx, ((ExecutionHashMap) inputJson).get("hello"));
        Assertions.assertEquals(expectedBytes, actualBytes);
        actualBytes = TbUtils.stringToBytes(ctx, ((ExecutionHashMap) inputJson).get("hello"), "UTF-8");
        Assertions.assertEquals(expectedBytes, actualBytes);

        expectedStr = "{\"hello\": 1234}";
        inputJson = TbUtils.decodeToJson(ctx, expectedStr);
        Object finalInputJson = inputJson;
        Assertions.assertThrows(IllegalAccessException.class, () -> TbUtils.stringToBytes(ctx, ((ExecutionHashMap) finalInputJson).get("hello")));
        Assertions.assertThrows(IllegalAccessException.class, () -> TbUtils.stringToBytes(ctx, ((ExecutionHashMap) finalInputJson).get("hello"), "UTF-8"));
    }

    @Test
    public void bytesFromList() {
        byte[] arrayBytes = {(byte) 0x00, (byte) 0x08, (byte) 0x10, (byte) 0x1C, (byte) 0xFF, (byte) 0xFC, (byte) 0xAD, (byte) 0x88, (byte) 0x75, (byte) 0x74, (byte) 0x8A, (byte) 0x82};
        Object[] arrayMix = {"0x00", 8, "16", "0x1C", 255, (byte) 0xFC, 173, 136, 117, 116, -118, "-126"};

        String expected = new String(arrayBytes);
        ArrayList<Byte> listBytes = new ArrayList<>(arrayBytes.length);
        for (Byte element : arrayBytes) {
            listBytes.add(element);
        }
        Assertions.assertEquals(expected, TbUtils.bytesToString(listBytes));

        ArrayList<Object> listMix = new ArrayList<>(arrayMix.length);
        Collections.addAll(listMix, arrayMix);
        Assertions.assertEquals(expected, TbUtils.bytesToString(listMix));
    }

    @Test
    public void bytesFromList_SpecSymbol() {
        List<String> listHex = new ArrayList<>(Arrays.asList("1D", "0x1D", "1F", "0x1F", "0x20", "0x20"));
        byte[] expectedBytes = new byte[]{29, 29, 31, 31, 32, 32};
        String actualStr = TbUtils.bytesToString(listHex);
        byte[] actualBytes = actualStr.getBytes();
        Assertions.assertArrayEquals(expectedBytes, actualBytes);
        Assertions.assertTrue(actualStr.isBlank());
        listHex = new ArrayList<>(Arrays.asList("0x21", "0x21"));
        expectedBytes = new byte[]{33, 33};
        actualStr = TbUtils.bytesToString(listHex);
        actualBytes = actualStr.getBytes();
        Assertions.assertArrayEquals(expectedBytes, actualBytes);
        Assertions.assertFalse(actualStr.isBlank());
        Assertions.assertEquals("!!", actualStr);
        listHex = new ArrayList<>(Arrays.asList("21", "0x21"));
        expectedBytes = new byte[]{21, 33};
        actualStr = TbUtils.bytesToString(listHex);
        actualBytes = actualStr.getBytes();
        Assertions.assertArrayEquals(expectedBytes, actualBytes);
        Assertions.assertFalse(actualStr.isBlank());
        Assertions.assertEquals("!", actualStr.substring(1));
        Assertions.assertEquals('\u0015', actualStr.charAt(0));
        Assertions.assertEquals(21, actualStr.charAt(0));
    }

    @Test
    public void bytesFromList_Error() {
        List<String> listHex = new ArrayList<>();
        listHex.add("0xFG");
        try {
            TbUtils.bytesToString(listHex);
            Assertions.fail("Should throw NumberFormatException");
        } catch (NumberFormatException e) {
            Assertions.assertTrue(e.getMessage().contains("Value: \"FG\" is not numeric or hexDecimal format!"));
        }

        List<String> listIntString = new ArrayList<>();
        listIntString.add("-129");
        try {
            TbUtils.bytesToString(listIntString);
            Assertions.fail("Should throw NumberFormatException");
        } catch (NumberFormatException e) {
            Assertions.assertTrue(e.getMessage().contains("The value '-129' could not be correctly converted to a byte. " +
                    "Integer to byte conversion requires the use of only 8 bits (with a range of min/max = -128/255)!"));
        }

        listIntString.add(0, "256");
        try {
            TbUtils.bytesToString(listIntString);
            Assertions.fail("Should throw NumberFormatException");
        } catch (NumberFormatException e) {
            Assertions.assertTrue(e.getMessage().contains("The value '256' could not be correctly converted to a byte. " +
                    "Integer to byte conversion requires the use of only 8 bits (with a range of min/max = -128/255)!"));
        }

        ArrayList<Integer> listIntBytes = new ArrayList<>();
        listIntBytes.add(-129);
        try {
            TbUtils.bytesToString(listIntBytes);
            Assertions.fail("Should throw NumberFormatException");
        } catch (NumberFormatException e) {
            Assertions.assertTrue(e.getMessage().contains("The value '-129' could not be correctly converted to a byte. " +
                    "Integer to byte conversion requires the use of only 8 bits (with a range of min/max = -128/255)!"));
        }

        listIntBytes.add(0, 256);
        try {
            TbUtils.bytesToString(listIntBytes);
            Assertions.fail("Should throw NumberFormatException");
        } catch (NumberFormatException e) {
            Assertions.assertTrue(e.getMessage().contains("The value '256' could not be correctly converted to a byte. " +
                    "Integer to byte conversion requires the use of only 8 bits (with a range of min/max = -128/255)!"));
        }

        ArrayList<Object> listObjects = new ArrayList<>();
        ArrayList<String> listStringObjects = new ArrayList<>();
        listStringObjects.add("0xFD");
        listObjects.add(listStringObjects);
        try {
            TbUtils.bytesToString(listObjects);
            Assertions.fail("Should throw NumberFormatException");
        } catch (NumberFormatException e) {
            Assertions.assertTrue(e.getMessage().contains("The value '[0xFD]' could not be correctly converted to a byte. " +
                    "Must be a HexDecimal/String/Integer/Byte format !"));
        }
    }

    @Test
    public void encodeDecodeUri_Test() {
        String uriOriginal = "-_.!~*'();/?:@&=+$,#ht://example.ж д a/path with spaces/?param1=Київ 1&param2=Україна2";
        String uriEncodeExpected = "-_.!~*'();/?:@&=+$,#ht://example.%D0%B6%20%D0%B4%20a/path%20with%20spaces/?param1=%D0%9A%D0%B8%D1%97%D0%B2%201&param2=%D0%A3%D0%BA%D1%80%D0%B0%D1%97%D0%BD%D0%B02";
        String uriEncodeActual = TbUtils.encodeURI(uriOriginal);
        Assertions.assertEquals(uriEncodeExpected, uriEncodeActual);

        String uriDecodeActual = TbUtils.decodeURI(uriEncodeActual);
        Assertions.assertEquals(uriOriginal, uriDecodeActual);
    }

    @Test
    public void intToHex_Test() {
        Assertions.assertEquals("FFF5EE", TbUtils.intToHex(-2578));
        Assertions.assertEquals("0xFFD8FFA6", TbUtils.intToHex(0xFFD8FFA6, true, true));
        Assertions.assertEquals("0xA6FFD8FF", TbUtils.intToHex(0xFFD8FFA6, false, true));
        Assertions.assertEquals("0x7FFFFFFF", TbUtils.intToHex(Integer.MAX_VALUE, true, true));
        Assertions.assertEquals("0x80000000", TbUtils.intToHex(Integer.MIN_VALUE, true, true));
        Assertions.assertEquals("0xAB", TbUtils.intToHex(0xAB, true, true));
        Assertions.assertEquals("0xABCD", TbUtils.intToHex(0xABCD, true, true));
        Assertions.assertEquals("0xABCDEF", TbUtils.intToHex(0xABCDEF, true, true));
        Assertions.assertEquals("0xCDAB", TbUtils.intToHex(0xABCDEF, false, true, 4));
        Assertions.assertEquals("0xAB", TbUtils.intToHex(171, true, true));
        Assertions.assertEquals("0xAB", TbUtils.intToHex(0xAB, false, true));
        Assertions.assertEquals("0xAB", TbUtils.intToHex(0xAB, true, true, 2));
        Assertions.assertEquals("AB", TbUtils.intToHex(0xAB, false, false, 2));
        Assertions.assertEquals("AB", TbUtils.intToHex(171, true, false));
        Assertions.assertEquals("0xAB", TbUtils.intToHex(0xAB, true, true));
        Assertions.assertEquals("0xAB", TbUtils.intToHex(0xAB, false, true));
        Assertions.assertEquals("AB", TbUtils.intToHex(0xAB, false, false));

        Assertions.assertEquals("0xABCD", TbUtils.intToHex(0xABCD, true, true));
        Assertions.assertEquals("0xCDAB", TbUtils.intToHex(0xABCD, false, true));
        Assertions.assertEquals("0xCD", TbUtils.intToHex(0xABCD, true, true, 2));
        Assertions.assertEquals("AB", TbUtils.intToHex(0xABCD, false, false, 2));
    }
    @Test
    public void longToHex_Test() {
        Assertions.assertEquals("0x7FFFFFFFFFFFFFFF", TbUtils.longToHex(Long.MAX_VALUE, true, true));
        Assertions.assertEquals("0x8000000000000000", TbUtils.longToHex(Long.MIN_VALUE, true, true));
        Assertions.assertEquals("0xFFD8FFA6FFD8FFA6", TbUtils.longToHex(0xFFD8FFA6FFD8FFA6L, true, true));
        Assertions.assertEquals("0xA6FFD8FFA6FFCEFF", TbUtils.longToHex(0xFFCEFFA6FFD8FFA6L, false, true));
        Assertions.assertEquals("0xAB", TbUtils.longToHex(0xABL, true, true));
        Assertions.assertEquals("0xABCD", TbUtils.longToHex(0xABCDL, true, true));
        Assertions.assertEquals("0xABCDEF", TbUtils.longToHex(0xABCDEFL, true, true));
        Assertions.assertEquals("0xABEFCDAB", TbUtils.longToHex(0xABCDEFABCDEFL, false, true, 8));
        Assertions.assertEquals("0xAB", TbUtils.longToHex(0xABL, true, true, 2));
        Assertions.assertEquals("AB", TbUtils.longToHex(0xABL, false, false, 2));

        Assertions.assertEquals("0xFFA6", TbUtils.longToHex(0xFFD8FFA6FFD8FFA6L, true, true, 4));
        Assertions.assertEquals("D8FF", TbUtils.longToHex(0xFFD8FFA6FFD8FFA6L, false, false, 4));
    }

    @Test
    public void numberToString_Test() {
        //       0011 0011 0110 0110 == 13158L
        //       1100 1100 1001 1010 == -13158L
        Assertions.assertEquals("11001101100110", TbUtils.intLongToString(13158L, 2));
        Assertions.assertEquals("1111111111111111111111111111111111111111111111111111111110011010", TbUtils.intLongToString(-102L, 2));
        Assertions.assertEquals("1111111111111111111111111111111111111111111111111100110010011010", TbUtils.intLongToString(-13158L, 2));
        Assertions.assertEquals("777777777777777777777", TbUtils.intLongToString(Long.MAX_VALUE, 8));
        Assertions.assertEquals("1000000000000000000000", TbUtils.intLongToString(Long.MIN_VALUE, 8));
        Assertions.assertEquals("9223372036854775807", TbUtils.intLongToString(Long.MAX_VALUE));
        Assertions.assertEquals("-9223372036854775808", TbUtils.intLongToString(Long.MIN_VALUE));
        Assertions.assertEquals("3366", TbUtils.intLongToString(13158L, 16));
        Assertions.assertEquals("FFCC9A", TbUtils.intLongToString(-13158L, 16));
        Assertions.assertEquals("0xFFCC9A", TbUtils.intLongToString(-13158L, 16, true, true));
        Assertions.assertEquals("hazelnut", TbUtils.intLongToString(1356099454469L, MAX_RADIX));
    }

    @Test
    public void intToHexWithPrintUnsignedBytes_Test() {
        Integer value = -40;
        String intToHexLe = TbUtils.intToHex(value, false, true);
        String intToHexBe = TbUtils.intToHex(value, true, true);

        List<Byte> hexTopByteLe = TbUtils.hexToBytes(ctx, intToHexLe);
        List<Byte> hexTopByteBe = TbUtils.hexToBytes(ctx, intToHexBe);

        byte[] arrayBytes = {-40, -1};
        List<Byte> expectedHexTopByteLe = Bytes.asList(arrayBytes);
        List<Byte> expectedHexTopByteBe = Lists.reverse(expectedHexTopByteLe);
        Assertions.assertEquals(expectedHexTopByteLe, hexTopByteLe);
        Assertions.assertEquals(expectedHexTopByteBe, hexTopByteBe);

        List<Integer> actualLe = TbUtils.printUnsignedBytes(ctx, hexTopByteLe);
        List<Integer> actualBe = TbUtils.printUnsignedBytes(ctx, hexTopByteBe);
        List<Integer> expectedLe = Ints.asList(216, 255);
        List<Integer> expectedBe = Lists.reverse(expectedLe);
        Assertions.assertEquals(expectedLe, actualLe);
        Assertions.assertEquals(expectedBe, actualBe);
    }

    @Test
    public void floatToHex_Test() {
        Float value = 20.89f;
        String expectedHex = "0x41A71EB8";
        String valueHexRev = "0xB81EA741";
        String actual = TbUtils.floatToHex(value);
        Assertions.assertEquals(expectedHex, actual);
        Float valueActual = TbUtils.parseHexToFloat(actual);
        Assertions.assertEquals(value, valueActual);
        valueActual = TbUtils.parseHexToFloat(valueHexRev, false);
        Assertions.assertEquals(value, valueActual);
    }
    @Test
    public void doubleToHex_Test() {
        String expectedHex = "0x409B04B10CB295EA";
        String actual = TbUtils.doubleToHex(doubleVal);
        Assertions.assertEquals(expectedHex, actual);
        Double valueActual = TbUtils.parseHexToDouble(actual);
        Assertions.assertEquals(doubleVal, valueActual);
        actual = TbUtils.doubleToHex(doubleVal, false);
        String expectedHexRev = "0xEA95B20CB1049B40";
        Assertions.assertEquals(expectedHexRev, actual);
    }

    @Test
    public void newError_Test() {
        String message = "frequency_weighting_type must be 0, 1 or 2.";
        Object value = 4;
        try {
            TbUtils.newError(message, value);
            Assertions.fail("Should throw NumberFormatException");
        } catch (RuntimeException e) {
            Assertions.assertTrue(e.getMessage().contains("frequency_weighting_type must be 0, 1 or 2. Value = 4"));
        }
        try {
            TbUtils.newError(message);
            Assertions.fail("Should throw NumberFormatException");
        } catch (RuntimeException e) {
            Assertions.assertTrue(e.getMessage().contains("frequency_weighting_type must be 0, 1 or 2."));
        }
    }

    @Test
    public void isNumeric_Test() {


    }

    private static List<Byte> toList(byte[] data) {
        List<Byte> result = new ArrayList<>(data.length);
        for (Byte b : data) {
            result.add(b);
        }
        return result;
    }
}

