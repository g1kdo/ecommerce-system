package rw.smart.ecommerce;

import javafx.application.Application;
import javafx.stage.Stage;
import rw.smart.ecommerce.utils.ui.Navigation;

/**
 * JavaFX entry point. The app opens on the sign-in screen; once authenticated,
 * MainShellController owns navigation between the feature screens.
 */
public class MainApplication extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("Smart E-Commerce System");
        Navigation.showLogin(stage);
        stage.show();
    }
}
