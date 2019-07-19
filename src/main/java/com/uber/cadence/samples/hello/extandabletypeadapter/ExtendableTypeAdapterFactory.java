package com.uber.cadence.samples.hello.extandabletypeadapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

// Could not managed to bound TOutput to both TInput and Extendable. It must extends both, actually.
public class ExtendableTypeAdapterFactory<TInput, TOutput extends Extendable> implements TypeAdapterFactory {

  private Class<TInput> inputClass;
  private Class<TOutput> outputClass;

  public ExtendableTypeAdapterFactory(Class<TInput> inputClass, Class<TOutput> outputClass) {

    this.inputClass = inputClass;
    this.outputClass = outputClass;
  }

  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    // directly inputClass (on read) and for outputClass (on write)
    if (inputClass != type.getRawType() && outputClass != type.getRawType()) {
      return null;
    }
    return new ExtendableTypeAdapter(gson, this, inputClass, outputClass).nullSafe();
  }
}
