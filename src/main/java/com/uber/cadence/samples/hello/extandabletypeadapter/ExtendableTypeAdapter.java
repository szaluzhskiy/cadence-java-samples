package com.uber.cadence.samples.hello.extandabletypeadapter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ExtendableTypeAdapter<T> extends TypeAdapter<T> {

  private final Gson gson;
  private final TypeAdapterFactory skipPast;
  private final Class inputClass;
  private final Class outputClass;

  public ExtendableTypeAdapter(Gson gson, TypeAdapterFactory skipPast, Class inputClass, Class outputClass) {
    this.gson = gson;
    this.skipPast = skipPast;
    this.inputClass = inputClass;
    this.outputClass = outputClass;
  }

  @Override
  public void write(JsonWriter out, T value) throws IOException {
    //write known properties
    TypeAdapter exceptionTypeAdapter =
        gson.getDelegateAdapter(skipPast, TypeToken.get(value.getClass()));
    JsonObject object = exceptionTypeAdapter.toJsonTree(value).getAsJsonObject();
    TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);

    //write extended properties
    Map<String, Object> extendedProperties = ((Extendable) value).getExtension();
    for (Map.Entry<String, Object> entry : extendedProperties.entrySet()) {
      object.add(entry.getKey(), gson.toJsonTree(entry.getValue()));
    }

    elementAdapter.write(out, object);
  }

  @Override
  public T read(JsonReader in) throws IOException {
    // read known fields
    TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);
    JsonObject object = elementAdapter.read(in).getAsJsonObject();
    TypeAdapter exceptionTypeAdapter =
        gson.getDelegateAdapter(skipPast, TypeToken.get(outputClass));
    Extendable result = (Extendable) exceptionTypeAdapter.fromJsonTree(object);

//    // get names of the known properties
//    Set<String> knownProperties = Arrays.stream(inputClass.getDeclaredFields())
//        .map(Field::getName)
//        .collect(Collectors.toSet());
//
//    // read extended properties
//    Map<String, Object> extendedProperties = object.keySet()
//        .stream()
//        .filter(x -> !knownProperties.contains(x))
//        .collect(Collectors.toMap(key -> key, object::get));
//    result.setExtension(extendedProperties);

    return (T) result;
  }
}
