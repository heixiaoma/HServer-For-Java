package net.hserver.init;

import net.hserver.bean.User;
import net.hserver.log.Log;
import top.hserver.core.interfaces.InitRunner;
import top.hserver.core.ioc.annotation.Autowired;
import top.hserver.core.ioc.annotation.Bean;
import top.hserver.core.ioc.annotation.Order;
import top.hserver.core.ioc.annotation.Value;

@Bean
@Order(1)
public class RunInit implements InitRunner {


  @Autowired
  private User user;

  @Override
  public void init(String[] args) {
    System.out.println("初始化方法：1-->"+user.getName());
  }
}
