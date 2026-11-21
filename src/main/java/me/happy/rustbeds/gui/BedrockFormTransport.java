package me.happy.rustbeds.gui;

import org.bukkit.Bukkit;
import org.geysermc.api.connection.Connection;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

record BedrockFormTransport(Class<?> apiType, Object api, UUID target, Runnable close) {
    private static final String CUMULUS = "org.geysermc.cumulus.";

    private static final ClassValue<FormApi> FORM_APIS = new ClassValue<>() {
        @Override
        protected FormApi computeValue(Class<?> apiType) {
            try {
                ClassLoader loader = apiType.getClassLoader();
                Class<?> builder = loader.loadClass(CUMULUS + "form.util.FormBuilder");
                Method build = builder.getMethod("build");
                Method simple = loader.loadClass(CUMULUS + "form.SimpleForm").getMethod("builder");
                Method custom = loader.loadClass(CUMULUS + "form.CustomForm").getMethod("builder");
                return new FormApi(findSendMethod(apiType, build.getReturnType()), builder.getMethod("title", String.class),
                        builder.getMethod("validResultHandler", Consumer.class),
                        builder.getMethod("closedOrInvalidResultHandler", Runnable.class), build,
                        simple, simple.getReturnType().getMethod("content", String.class),
                        simple.getReturnType().getMethod("button", String.class),
                        loader.loadClass(CUMULUS + "response.SimpleFormResponse").getMethod("clickedButtonId"),
                        custom, custom.getReturnType().getMethod("input", String.class, String.class, String.class),
                        loader.loadClass(CUMULUS + "response.CustomFormResponse").getMethod("asInput", int.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot bind the Bedrock form API for " + apiType.getName(), e);
            }
        }
    };

    private static Method findSendMethod(Class<?> apiType, Class<?> form) throws NoSuchMethodException {
        try {
            return apiType.getMethod("sendForm", form);
        } catch (NoSuchMethodException ignored) {
            return apiType.getMethod("sendForm", UUID.class, form);
        }
    }

    static BedrockFormTransport find(UUID playerId) {
        if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            GeyserApi geyser = GeyserApi.api();
            GeyserConnection connection = geyser == null ? null : geyser.connectionByUuid(playerId);
            if (connection != null) {
                return new BedrockFormTransport(Connection.class, connection, null, () -> connection.closeForm());
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            FloodgateApi floodgate = FloodgateApi.getInstance();
            if (floodgate != null && floodgate.isFloodgatePlayer(playerId)) {
                return new BedrockFormTransport(FloodgateApi.class, floodgate, playerId,
                        () -> floodgate.closeForm(playerId));
            }
        }

        return null;
    }

    boolean sendChoices(String title, String content, List<String> buttons, IntConsumer onSelect,
            Runnable onClose) throws ReflectiveOperationException {
        // Each provider builds its forms with the Cumulus classes visible to its own API.
        FormApi forms = FORM_APIS.get(apiType);
        Object builder = forms.simple().invoke(null);
        forms.content().invoke(builder, content);
        for (String button : buttons) {
            forms.button().invoke(builder, button);
        }
        return send(forms, builder, title, forms.clickedButtonId(), value -> onSelect.accept((int) value), onClose);
    }

    boolean sendRename(String title, String label, String currentName, Consumer<String> onSubmit,
            Runnable onClose) throws ReflectiveOperationException {
        FormApi forms = FORM_APIS.get(apiType);
        Object builder = forms.custom().invoke(null);
        forms.input().invoke(builder, label, "", currentName);
        return send(forms, builder, title, forms.asInput(), value -> onSubmit.accept((String) value), onClose, 0);
    }

    private boolean send(FormApi forms, Object builder, String title, Method readResult,
            Consumer<Object> onResult, Runnable onClose, Object... resultArgs) throws ReflectiveOperationException {
        Consumer<Object> handler = response -> {
            Object value;
            try {
                value = readResult.invoke(response, resultArgs);
            } catch (ReflectiveOperationException e) {
                onClose.run();
                return;
            }
            onResult.accept(value);
        };
        forms.title().invoke(builder, title);
        forms.validResultHandler().invoke(builder, handler);
        forms.closedOrInvalidResultHandler().invoke(builder, onClose);
        Object form = forms.build().invoke(builder);
        Method sendForm = forms.sendForm();
        return (boolean) (target == null ? sendForm.invoke(api, form) : sendForm.invoke(api, target, form));
    }

    private record FormApi(Method sendForm, Method title, Method validResultHandler,
                           Method closedOrInvalidResultHandler, Method build,
                           Method simple, Method content, Method button, Method clickedButtonId,
                           Method custom, Method input, Method asInput) {
    }
}
