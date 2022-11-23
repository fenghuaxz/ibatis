package org.ibatis.extension;

public interface Jdbc {

    String OMIT = "@omit";

    <T> T getMapper(Class<T> mapper);
}