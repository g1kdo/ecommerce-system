module rw.smart.ecommerce {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires java.sql;
    requires org.kordamp.ikonli.fontawesome5;

    // NoSQL document store (review content + system logs)
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;

    opens rw.smart.ecommerce to javafx.fxml;
    exports rw.smart.ecommerce;

    opens rw.smart.ecommerce.controller to javafx.fxml;
}