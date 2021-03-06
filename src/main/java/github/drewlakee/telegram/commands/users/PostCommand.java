package github.drewlakee.telegram.commands.users;

import github.drewlakee.telegram.commands.BotCommand;
import github.drewlakee.telegram.commands.callbacks.PostCallback;
import github.drewlakee.telegram.commands.handlers.CallbackQueryHandler;
import github.drewlakee.telegram.commands.handlers.MessageHandler;
import github.drewlakee.telegram.utils.keyboards.HostGroupKeyboard;
import github.drewlakee.telegram.utils.keyboards.InlineKeyboardBuilder;
import github.drewlakee.telegram.utils.keyboards.NumpadKeyboardBuilder;
import github.drewlakee.telegram.utils.parsers.MessageKeysParser;
import github.drewlakee.telegram.utils.ResponseMessageDispatcher;
import github.drewlakee.vk.domain.attachments.VkAttachment;
import github.drewlakee.vk.domain.attachments.VkAudioAttachment;
import github.drewlakee.vk.domain.attachments.VkPhotoAttachment;
import github.drewlakee.vk.domain.groups.VkGroupFullWrapper;
import github.drewlakee.vk.domain.groups.VkGroupsCustodian;
import github.drewlakee.vk.services.VkWallPostService;
import github.drewlakee.vk.services.content.VkContentSearchStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class PostCommand extends BotCommand implements CallbackQueryHandler, MessageHandler {

    public final VkGroupsCustodian custodian;
    public final VkWallPostService vkWallPostService;
    public final VkContentSearchStrategy randomAudioContent;
    public final VkContentSearchStrategy randomPhotoContent;

    public static final int MAX_VK_ATTACHMENTS = 10;

    @Autowired
    public PostCommand(VkGroupsCustodian custodian,
                       VkWallPostService vkWallPostService,
                       @Qualifier("vkRandomAudioSearch") VkContentSearchStrategy randomAudioContent,
                       @Qualifier("vkRandomPhotoSearch") VkContentSearchStrategy randomPhotoContent) {
        super("/post");
        this.custodian = custodian;
        this.vkWallPostService = vkWallPostService;
        this.randomAudioContent = randomAudioContent;
        this.randomPhotoContent = randomPhotoContent;
    }

    @Override
    public void handle(AbsSender sender, Message message) {
        SendMessage response = new SendMessage();
        response.setChatId(message.getChatId());
        response.setText("Выбери кол-во пикч: ");
        NumpadKeyboardBuilder numpad = new NumpadKeyboardBuilder(4, MAX_VK_ATTACHMENTS);
        response.setReplyMarkup(numpad.build( getCommandName() + "_first_call_photo", true));
        ResponseMessageDispatcher.send(sender, response);
    }

    @Override
    public void handle(AbsSender sender, CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        PostCallback handleCallback = PostCallback.CONSTRUCT;
        int audiosQuantity = 0;
        int photosQuantity = 0;

        if (data.contains("photo_numpad")) {
            if (data.contains("_first_call_")) {
                photosQuantity = Integer.parseInt(data.replace(getCommandName() + "_first_call_photo_numpad", ""));
                if (photosQuantity < 10) {
                    handleCallback = PostCallback.CHANGE_AUDIO_QUANTITY;
                }
            } else {
                Map<String, String> keys = MessageKeysParser.parseMessageKeysBody(callbackQuery.getMessage().getText());
                photosQuantity = Integer.parseInt(data.replace(getCommandName() + "_photo_numpad", ""));
                audiosQuantity = Integer.parseInt(keys.get("треков_в_подборке"));
            }
        }

        if (data.contains("_audio_numpad")) {
            Map<String, String> keys = MessageKeysParser.parseMessageKeysBody(callbackQuery.getMessage().getText());
            photosQuantity = Integer.parseInt(keys.get("пикч_в_подборке"));
            audiosQuantity = Integer.parseInt(data.replace(getCommandName() + "_audio_numpad", ""));
        }

        if (data.contains(PostCallback.CHANGE_SET.toCallbackString(getCommandName()))) {
            Map<String, String> keys = MessageKeysParser.parseMessageKeysBody(callbackQuery.getMessage().getText());
            photosQuantity = Integer.parseInt(keys.get("пикч_в_подборке"));
            audiosQuantity = Integer.parseInt(keys.get("треков_в_подборке"));
        }

        if (data.contains(PostCallback.CHANGE_AUDIO_QUANTITY.toCallbackString(getCommandName()))) {
            handleCallback = PostCallback.CHANGE_AUDIO_QUANTITY;
            Map<String, String> keys = MessageKeysParser.parseMessageKeysBody(callbackQuery.getMessage().getText());
            photosQuantity = Integer.parseInt(keys.get("пикч_в_подборке"));
            audiosQuantity = Integer.parseInt(keys.get("треков_в_подборке"));
        }

        if (data.contains(PostCallback.CHANGE_PHOTO_QUANTITY.toCallbackString(getCommandName()))) {
            handleCallback = PostCallback.CHANGE_PHOTO_QUANTITY;
            Map<String, String> keys = MessageKeysParser.parseMessageKeysBody(callbackQuery.getMessage().getText());
            photosQuantity = Integer.parseInt(keys.get("пикч_в_подборке"));
            audiosQuantity = Integer.parseInt(keys.get("треков_в_подборке"));
        }

        if (data.equals(PostCallback.SEND.toCallbackString(getCommandName()))) {
            handleCallback = PostCallback.SEND;
        }

        if (data.equals(PostCallback.SEND_AGAIN.toCallbackString(getCommandName()))) {
            handleCallback = PostCallback.SEND_AGAIN;
        }

        int groupId = 0;
        if (data.contains(getCommandName() + "_group_id")) {
            handleCallback = PostCallback.GROUP;
            groupId = Integer.parseInt(data.replace(getCommandName() + "_group_id", ""));
        }


        if (data.contains(PostCallback.REFRESH_ONLY_AUDIO.toCallbackString(getCommandName()))) {
            handleCallback = PostCallback.REFRESH_ONLY_AUDIO;
            Map<String, String> keys = MessageKeysParser.parseMessageKeysBody(callbackQuery.getMessage().getText());
            photosQuantity = Integer.parseInt(keys.get("пикч_в_подборке"));
            audiosQuantity = Integer.parseInt(keys.get("треков_в_подборке"));
        }

        if (data.contains(PostCallback.REFRESH_ONLY_PHOTO.toCallbackString(getCommandName()))) {
            handleCallback = PostCallback.REFRESH_ONLY_PHOTO;
            Map<String, String> keys = MessageKeysParser.parseMessageKeysBody(callbackQuery.getMessage().getText());
            photosQuantity = Integer.parseInt(keys.get("пикч_в_подборке"));
            audiosQuantity = Integer.parseInt(keys.get("треков_в_подборке"));
        }

        switch (handleCallback) {
            case CONSTRUCT: sendContentSet(sender, callbackQuery, photosQuantity, audiosQuantity); break;
            case REFRESH_ONLY_AUDIO: sendContentSetWithOnlyAudioUpdated(sender, callbackQuery, photosQuantity, audiosQuantity); break;
            case REFRESH_ONLY_PHOTO: sendContentSetWithOnlyPhotoUpdated(sender, callbackQuery, photosQuantity, audiosQuantity); break;
            case CHANGE_PHOTO_QUANTITY: sendPhotoQuantityNumpad(sender, callbackQuery, audiosQuantity); break;
            case CHANGE_AUDIO_QUANTITY: sendAudioQuantityNumpad(sender, callbackQuery, photosQuantity); break;
            case GROUP: sendSetToGroup(sender, callbackQuery, groupId); break;
            case SEND:
            case SEND_AGAIN:
                sendGroupKeyboard(sender, callbackQuery);
        }
    }

    private void sendContentSet(AbsSender sender, CallbackQuery callbackQuery, int photosQuantity, int audiosQuantity) {
        EditMessageText response = new EditMessageText();
        response.setChatId(callbackQuery.getMessage().getChatId());
        response.setMessageId(callbackQuery.getMessage().getMessageId());
        response.setParseMode(ParseMode.HTML);

        List<VkAttachment> attachments = new ArrayList<>();

        if (photosQuantity > 0) {
            attachments.addAll(randomPhotoContent.search(photosQuantity));
        }

        if (audiosQuantity > 0) {
            attachments.addAll(randomAudioContent.search(audiosQuantity));
        }

        response.setText(fillTextBody(attachments, photosQuantity, audiosQuantity).toString());
        if (audiosQuantity > 0 || photosQuantity > 0) {
            response.setReplyMarkup(buildConstructKeyboard(audiosQuantity, photosQuantity));
        }

        ResponseMessageDispatcher.send(sender, response);
    }

    private void sendContentSetWithOnlyAudioUpdated(AbsSender sender, CallbackQuery callbackQuery, int photosQuantity, int audiosQuantity) {
        EditMessageText response = new EditMessageText();
        response.setChatId(callbackQuery.getMessage().getChatId());
        response.setMessageId(callbackQuery.getMessage().getMessageId());
        response.setParseMode(ParseMode.HTML);

        ArrayList<VkAttachment> attachments = new ArrayList<>();
        Map<String, String> keys = MessageKeysParser.parseMessageKeysBody(callbackQuery.getMessage().getText());
        List<String> rawPhotosIds = keys.entrySet().stream().filter(entry -> entry.getKey().startsWith("пикча")).map(Map.Entry::getValue).collect(Collectors.toList());
        List<MessageEntity> entities = callbackQuery.getMessage().getEntities();
        for (int i = 0; i < rawPhotosIds.size(); i++) {
            VkPhotoAttachment vkPhotoAttachment = new VkPhotoAttachment();
            vkPhotoAttachment.setPrettyVkAttachmentString(rawPhotosIds.get(i));
            vkPhotoAttachment.setLargestSizeUrl(entities.get(i).getUrl());
            attachments.add(vkPhotoAttachment);
        }

        attachments.addAll(randomAudioContent.search(audiosQuantity));

        response.setText(fillTextBody(attachments, photosQuantity, audiosQuantity).toString());
        response.setReplyMarkup(buildConstructKeyboard(audiosQuantity, photosQuantity));

        ResponseMessageDispatcher.send(sender, response);
    }

    private void sendContentSetWithOnlyPhotoUpdated(AbsSender sender, CallbackQuery callbackQuery, int photosQuantity, int audiosQuantity) {
        EditMessageText response = new EditMessageText();
        response.setChatId(callbackQuery.getMessage().getChatId());
        response.setMessageId(callbackQuery.getMessage().getMessageId());
        response.setParseMode(ParseMode.HTML);

        ArrayList<VkAttachment> attachments = new ArrayList<>(randomPhotoContent.search(photosQuantity));

        StringBuilder textBodyForResponse = fillTextBody(attachments, photosQuantity, audiosQuantity);

        Arrays.stream(callbackQuery.getMessage().getText().split("\n"))
                .filter(lineInMessage -> lineInMessage.startsWith("трек_"))
                .forEach(rawStringLineWithTrack -> textBodyForResponse.append(rawStringLineWithTrack).append("\n"));

        response.setText(textBodyForResponse.toString());
        response.setReplyMarkup(buildConstructKeyboard(audiosQuantity, photosQuantity));

        ResponseMessageDispatcher.send(sender, response);
    }

    private StringBuilder fillTextBody(List<VkAttachment> attachments, int photosQuantity, int audiosQuantity) {
        StringBuilder text = new StringBuilder();
        if (audiosQuantity == 0 && photosQuantity == 0) {
            text.append("Для подборки нужно указать правильные данные.");
            return text;
        } else {
            text.append("пикч_в_подборке: ").append(photosQuantity).append("\n");
            text.append("треков_в_подборке: ").append(audiosQuantity).append("\n");
            text.append("\n");
        }

        if (photosQuantity > 0) {
            List<VkAttachment> photos = attachments.stream()
                    .filter(attach -> attach instanceof VkPhotoAttachment)
                    .collect(Collectors.toList());

            int count = 1;
            for (VkAttachment photo : photos) {
                VkPhotoAttachment vkCustomPhoto = (VkPhotoAttachment) photo;
                String photoURL = vkCustomPhoto.getLargestSizeUrl();
                String vkAttachmentFormat = photo.toPrettyVkAttachmentString();

                text.append("пикча_").append(count).append(": ");
                text.append("<a href=\"").append(photoURL).append("\">").append(vkAttachmentFormat).append("</a>");
                text.append("\n");
                count++;
            }
        }

        if (photosQuantity > 0 && audiosQuantity > 0) {
            text.append("\n");
        }

        if (audiosQuantity > 0) {
            List<VkAttachment> audios = attachments.stream()
                    .filter(attach -> attach instanceof VkAudioAttachment)
                    .collect(Collectors.toList());

            int count = 1;
            for (VkAttachment audio : audios) {
                VkAudioAttachment vkAudioAttachment = (VkAudioAttachment) audio;
                String prettyNameOfAudio = vkAudioAttachment.toPrettyString();
                String vkAttachmentFormat = audio.toPrettyVkAttachmentString();

                text.append("трек_").append(count).append(": ");
                text.append(vkAttachmentFormat).append(" (").append(prettyNameOfAudio).append(")");
                text.append("\n");
                count++;
            }
        }

        return text;
    }

    private void sendAudioQuantityNumpad(AbsSender sender, CallbackQuery callbackQuery, int photoQuantity) {
        EditMessageText response = new EditMessageText();
        response.setChatId(callbackQuery.getMessage().getChatId());
        response.setMessageId(callbackQuery.getMessage().getMessageId());
        response.setText("пикч_в_подборке: " + photoQuantity + "\n\nВыбери кол-во треков: ");
        NumpadKeyboardBuilder numpad = new NumpadKeyboardBuilder(4, MAX_VK_ATTACHMENTS - photoQuantity);
        response.setReplyMarkup(numpad.build(getCommandName() + "_audio", true));
        ResponseMessageDispatcher.send(sender, response);
    }

    private void sendPhotoQuantityNumpad(AbsSender sender, CallbackQuery callbackQuery, int audioQuantity) {
        EditMessageText response = new EditMessageText();
        response.setChatId(callbackQuery.getMessage().getChatId());
        response.setMessageId(callbackQuery.getMessage().getMessageId());
        response.setText("треков_в_подборке: " + audioQuantity + "\n\nВыбери кол-во пикч: ");
        NumpadKeyboardBuilder numpad = new NumpadKeyboardBuilder(4, MAX_VK_ATTACHMENTS - audioQuantity);
        response.setReplyMarkup(numpad.build(getCommandName() + "_photo", true));
        ResponseMessageDispatcher.send(sender, response);
    }

    private void sendSetToGroup(AbsSender sender, CallbackQuery callbackQuery, int groupId) {
        Map<String, String> keys = MessageKeysParser.parseMessageKeysBody(callbackQuery.getMessage().getText());
        List<String> audiosKeys = keys.keySet().stream().filter(key -> key.startsWith("трек_")).collect(Collectors.toList());
        List<String> photosKeys = keys.keySet().stream().filter(key -> key.startsWith("пикча_")).collect(Collectors.toList());
        List<String> vkAttachments = new ArrayList<>();

        for (String key : audiosKeys) {
            vkAttachments.add(keys.get(key));
        }

        for (String key : photosKeys) {
            vkAttachments.add(keys.get(key));
        }

        Optional<VkGroupFullWrapper> first = custodian.getGroupsWithEditableRights().stream().filter(group -> group.getGroupFull().getId() == groupId).findFirst();

        EditMessageReplyMarkup response = new EditMessageReplyMarkup();
        response.setChatId(callbackQuery.getMessage().getChatId());
        response.setMessageId(callbackQuery.getMessage().getMessageId());
        InlineKeyboardBuilder keyboardBuilder = new InlineKeyboardBuilder();
        boolean isRequestSuccessful = vkWallPostService.makePost(first.orElseThrow(), vkAttachments);
        if (isRequestSuccessful) {
            keyboardBuilder
                    .addButton(new InlineKeyboardButton()
                            .setText("Пост отправлен в " + first.orElseThrow().getGroupFull().getName() + " группу!")
                            .setUrl("https://vk.com/" + first.orElseThrow().getGroupFull().getScreenName()))
                    .nextLine()
                    .addButton(new InlineKeyboardButton()
                            .setText("Отправить еще раз")
                            .setCallbackData(PostCallback.SEND_AGAIN.toCallbackString(getCommandName()))
                    );
        } else {
            keyboardBuilder.addButton(new InlineKeyboardButton()
                    .setText("Oops... something was broken on the way!")
                    .setUrl("https://vk.com/%20error"));
        }

        response.setReplyMarkup(keyboardBuilder.build());
        ResponseMessageDispatcher.send(sender, response);
    }

    private void sendGroupKeyboard(AbsSender sender, CallbackQuery callbackQuery) {
        EditMessageReplyMarkup response = new EditMessageReplyMarkup();
        response.setChatId(callbackQuery.getMessage().getChatId());
        response.setMessageId(callbackQuery.getMessage().getMessageId());
        response.setReplyMarkup(new HostGroupKeyboard(custodian).build(getCommandName(), true));
        ResponseMessageDispatcher.send(sender, response);
    }

    private InlineKeyboardMarkup buildConstructKeyboard(int audioQuantity, int photoQuantity) {
        InlineKeyboardBuilder postKeyboard = new InlineKeyboardBuilder();
        postKeyboard.addButton(new InlineKeyboardButton()
                .setText("Обновить подборку")
                .setCallbackData(PostCallback.CHANGE_SET.toCallbackString(getCommandName())))
                .nextLine();

        if (audioQuantity > 0) {
            postKeyboard
                    .addButton(new InlineKeyboardButton()
                        .setText("Обновить подборку треков")
                        .setCallbackData(PostCallback.REFRESH_ONLY_AUDIO.toCallbackString(getCommandName())));
        }

        if (photoQuantity > 0) {
            postKeyboard
                    .addButton(new InlineKeyboardButton()
                        .setText("Обновить подборку пикч")
                        .setCallbackData(PostCallback.REFRESH_ONLY_PHOTO.toCallbackString(getCommandName())));
        }

        if (photoQuantity > 0 || audioQuantity > 0) {
            postKeyboard.nextLine();
        }

        postKeyboard.addButton(new InlineKeyboardButton()
                        .setText("Изменить кол-во треков")
                        .setCallbackData(PostCallback.CHANGE_AUDIO_QUANTITY.toCallbackString(getCommandName())))
                .addButton(new InlineKeyboardButton()
                        .setText("Изменить кол-во пикч")
                        .setCallbackData(PostCallback.CHANGE_PHOTO_QUANTITY.toCallbackString(getCommandName())))
                .nextLine()
                .addButton(new InlineKeyboardButton()
                        .setText("Отправить в группу")
                        .setCallbackData(PostCallback.SEND.toCallbackString(getCommandName())))
                .nextLine()
                .addButton(new InlineKeyboardButton()
                        .setText("Отменить запрос")
                        .setCallbackData("/deleteMessage"));


        return postKeyboard.build();
    }
}
