package org.granite.tide.javafx;

import java.io.IOException;
import java.io.InputStream;

import javafx.fxml.FXMLLoader;
import javafx.util.Callback;

import org.granite.tide.Context;


public class TideFXMLLoader {
	
    public static Object load(final Context context, String url, Class<?> controllerClass) throws IOException {
        InputStream fxmlStream = null;
        try {
            fxmlStream = controllerClass.getResourceAsStream(url);
            FXMLLoader loader = new FXMLLoader();
            loader.setControllerFactory(new Callback<Class<?>, Object>() {
				@Override
				public Object call(Class<?> type) {
					return context.byType(type);
				}
            });
            for (String name : context.allNames())
            	loader.getNamespace().put(name, context.byName(name));
            return loader.load(fxmlStream);
        }
        finally {
            if (fxmlStream != null) {
                fxmlStream.close();
            }
        }
    }
}
