package net.hserver.service;

import net.hserver.log.Log;
import top.hserver.core.ioc.annotation.Bean;

@Bean("t2")
public class T2ServiceImpl implements TService {
    @Log
    @Override
    public String t() {

        System.out.println(1/0);

        return "tt111";
    }
}
