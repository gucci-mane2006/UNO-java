#!/bin/bash
java --module-path lib --add-modules javafx.controls,javafx.fxml,javafx.graphics --enable-native-access=javafx.graphics -Djava.library.path=bin -jar UNO-java.jar