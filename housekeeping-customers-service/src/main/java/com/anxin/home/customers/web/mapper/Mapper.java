package com.anxin.home.customers.web.mapper;

public interface Mapper<R, E> {
    E map(E response, R request);
}
