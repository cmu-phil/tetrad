@ECHO OFF
:: Tetred-CLI JAR file
SET JAR=${project.artifactId}-${project.version}-jar-with-dependencies.jar

java -jar %JAR% --algorithm fgs %*

