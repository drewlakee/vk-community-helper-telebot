package telegram.commands;

import app.Environment;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.photos.Photo;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import telegram.commands.abstractions.BotCommand;
import telegram.commands.abstractions.CallbackQueryHandler;
import telegram.commands.abstractions.MessageHandler;
import vk.api.VkApi;
import vk.api.VkUserActor;
import vk.domain.vkObjects.VkCustomAudio;
import vk.services.VkContentFinder;

public class RandomCommand extends BotCommand implements CallbackQueryHandler, MessageHandler {

    public RandomCommand() {
        super("/random");
    }

    @Override
    public void handle(AbsSender sender, CallbackQuery callbackQuery) {

    }

    @Override
    public void handle(AbsSender sender, Message message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(message.getChatId());
        sendMessage.setText("");

        try {
            Photo randomPhoto = VkContentFinder.findRandomPhoto();
            VkCustomAudio randomAudio = VkContentFinder.findRandomAudio();
            String photoAttachment = "photo" + randomPhoto.getOwnerId() + "_" + randomPhoto.getId();
            String audioAttachment = "audio" + randomAudio.getOwnerId() + "_" + randomAudio.getId();
            VkApi.instance()
                    .wall()
                    .post(VkUserActor.instance())
                    .ownerId(Integer.parseInt(Environment.PROPERTIES.get("my_public").toString()))
                    .attachments(photoAttachment, audioAttachment)
                    .execute();
        } catch (ClientException | ApiException e) {
            sendMessage.setText("Что-то по пути сломалось...");
        }

        boolean isOk = sendMessage.getText().isEmpty();
        if (isOk)
            sendMessage.setText("Готово, чекай группу \uD83D\uDE38");
        sendAnswerMessage(sender, sendMessage);
    }
}