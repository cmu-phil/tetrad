@ECHO OFF
:: Tetred-CLI JAR file
SET JAR=@ARTIFACT_ID@-@VERSION@-jar-with-dependencies.jar

java -jar %JAR% --algorithm fgs %*

