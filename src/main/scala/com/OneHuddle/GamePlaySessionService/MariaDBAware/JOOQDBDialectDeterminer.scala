package com.OneHuddle.GamePlaySessionService.MariaDBAware

import org.jooq.SQLDialect

/**
  * Created by nirmalya on 19/12/17.
  */
trait JOOQDBDialectDeterminer {

   def chooseDialect(requiredDialectAsProperty: String): SQLDialect = {

     requiredDialectAsProperty match {

       case "MARIADB" => SQLDialect.MARIADB
       case "MYSQL"   => SQLDialect.MYSQL_5_7
     }
   }

}
