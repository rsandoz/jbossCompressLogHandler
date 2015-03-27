# jbossCompressLogHandler
compressing log file handler for jboss


Compile against jBoss client jar:

C:\>javac -d . -cp C: \jboss-as-7.1.1.Final\bin\client\jboss-client.jar;. CompressHandler.java



Build the CompressHandler.class into a jarfile:

jar cvf CompressHandler.jar loggers



Create directory "loggers\main" in jboss modules:

C:\jboss-as-7.1.1.Final\modules\loggers\main\



Copy jar and module.xml into this folder:

copy C:\source\customLogger\CompressHandler.jar C: \jboss-as-7.1.1.Final\modules\loggers\main\

copy C:\source\customLogger\module.xml C: \jboss-as-7.1.1.Final\modules\loggers\main\
