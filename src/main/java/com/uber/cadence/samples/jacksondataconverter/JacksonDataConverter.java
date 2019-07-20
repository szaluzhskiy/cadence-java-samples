package com.uber.cadence.samples.jacksondataconverter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Defaults;
import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.converter.DataConverterException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public class JacksonDataConverter implements DataConverter {
  private static final DataConverter INSTANCE = new JacksonDataConverter();
  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  private final ObjectMapper jackson = new ObjectMapper();

  public static DataConverter getInstance() {
    return INSTANCE;
  }

  private JacksonDataConverter() {}

  /**
   * When values is empty or it contains a single value and it is null then return empty blob. If a
   * single value do not wrap it into Json array. Exception stack traces are converted to a single
   * string stack trace to save space and make them more readable.
   */
  @Override
  public byte[] toData(Object... values) throws DataConverterException {
    if (values == null || values.length == 0) {
      return null;
    }
    try {
      if (values.length == 1) {
        Object value = values[0];
        String json = jackson.writeValueAsString(value);
        return json.getBytes(StandardCharsets.UTF_8);
      }
      String json = jackson.writeValueAsString(values);
      System.out.println("to data result string: " + json);
      return json.getBytes(StandardCharsets.UTF_8);
    } catch (DataConverterException e) {
      throw e;
    } catch (Throwable e) {
      throw new DataConverterException(e);
    }
  }

  @Override
  public <T> T fromData(byte[] content, Class<T> valueClass, Type valueType)
      throws DataConverterException {
    if (content == null) {
      return null;
    }
    try {
      return jackson.readValue(new String(content, StandardCharsets.UTF_8), valueClass);
    } catch (Exception e) {
      throw new DataConverterException(content, new Type[] {valueType}, e);
    }
  }

  @Override
  public Object[] fromDataArray(byte[] content, Type... valueTypes) throws DataConverterException {
    try {
      if (content == null) {
        if (valueTypes.length == 0) {
          return EMPTY_OBJECT_ARRAY;
        }
        throw new DataConverterException(
            "Content doesn't match expected arguments", content, valueTypes);
      }
      if (valueTypes.length == 1) {
        Object result = readByType(content, valueTypes[0]);
        //        Object result = gson.fromJson(new String(content, StandardCharsets.UTF_8),
        // valueTypes[0]);
        return new Object[] {result};
      }

      final JsonNode element = jackson.readTree(new String(content, StandardCharsets.UTF_8));
      //      JsonElement element = parser.parse(new String(content, StandardCharsets.UTF_8));
      ArrayNode array;
      //      if (element instanceof JsonArray) {
      if (element instanceof ArrayNode) {
        array = (ArrayNode) element;
      } else {
        array = jackson.createArrayNode();
        array.add(element);
      }

      Object[] result = new Object[valueTypes.length];
      for (int i = 0; i < valueTypes.length; i++) {

        if (i >= array.size()) { // Missing arugments => add defaults
          Type t = valueTypes[i];
          if (t instanceof Class) {
            result[i] = Defaults.defaultValue((Class<?>) t);
          } else {
            result[i] = null;
          }
        } else {
          //          result[i] = gson.fromJson(array.get(i), valueTypes[i]);
          final Class<?> aClass = TypeFactory.rawClass(valueTypes[i]);
          final JsonNode jsonNode = array.get(i);
          result[i] = jackson.convertValue(jsonNode, aClass);
        }
      }
      return result;
    } catch (DataConverterException e) {
      throw e;
    } catch (Exception e) {
      throw new DataConverterException(content, valueTypes, e);
    }
  }

  private Object readByType(byte[] content, Type valueType) throws IOException {
    final Class<?> aClass = TypeFactory.rawClass(valueType);
    return jackson.readValue(new String(content, StandardCharsets.UTF_8), aClass);
  }
}
