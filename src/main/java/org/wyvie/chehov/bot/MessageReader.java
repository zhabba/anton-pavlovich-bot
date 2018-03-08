package org.wyvie.chehov.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.*;
import com.pengrad.telegrambot.response.GetChatResponse;
import com.pengrad.telegrambot.response.GetUpdatesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.wyvie.chehov.TelegramProperties;
import org.wyvie.chehov.bot.commands.CommandProcessor;

import java.util.List;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class MessageReader {

    private final Logger logger = LoggerFactory.getLogger(MessageReader.class);

    private CommandProcessor commandProcessor;
    private TelegramBot telegramBot;
    private TelegramProperties telegramProperties;
    private User botUser;

    private int lastOffset;

    @Autowired
    public MessageReader(CommandProcessor commandProcessor,
                         TelegramProperties telegramProperties,
                         @Qualifier("telegramBot") TelegramBot telegramBot,
                         @Qualifier("botUser") User botUser) {

        this.commandProcessor = commandProcessor;
        this.telegramBot = telegramBot;
        this.telegramProperties = telegramProperties;
        this.botUser = botUser;

        this.lastOffset = 0;
    }

    @Scheduled(fixedDelay = 200)
    public void readMessages() {
        GetUpdates getUpdates = new GetUpdates()
                .limit(telegramProperties.getUpdateLimit())
                .offset(lastOffset)
                .timeout(0);

        GetUpdatesResponse response = telegramBot.execute(getUpdates);
        List<Update> updates = response.updates();

        for (Update update : updates) {
            lastOffset = update.updateId() + 1;

            Message message = update.message();

            logger.debug("Got message '" + message.text() + "' from chat_id " + message.chat().id());

            if (validateCommmand(message))
                commandProcessor.processCommand(message);

            // if there are new people in chat, greet them
            if (!greetNewPeople(message))
                return;

            lastOffset = update.updateId() + 1;
        }
    }

    /**
     * Validates this is the command we would like to process
     * @param message message from Telegram chat
     * @return true if we want to process the command, false otherwise
     */
    private boolean validateCommmand(Message message) {
        String messageText = message.text();

        // if starts with '/' symbol, it's a command
        if (!StringUtils.isEmpty(messageText) &&
                messageText.startsWith("/")) {

            messageText = messageText.toLowerCase().trim();

            String command = messageText.contains(" ") ?
                    messageText.split(" ")[0] : messageText;

            // talking to another bot here
            if (command.contains("@") &&
                    !command.endsWith("@" + botUser.username())) {

                return false;
            }

            return true;
        }

        // not a command
        return false;
    }

    /**
     * Used to greet new people in chat and attach pinned message
     * @param message message we got from update method
     * @return false if we were successful in command processing,
     *         true otherwise
     */
    private boolean greetNewPeople(Message message) {
        User[] users = message.newChatMembers();

        if (users == null)
            return true;

        Long chatId = message.chat().id();
        GetChat getChat = new GetChat(chatId);
        GetChatResponse chatResponse = telegramBot.execute(getChat);
        if (!chatResponse.isOk())
            return false;

        Message pinnedMessage = chatResponse.chat().pinnedMessage();

        // if there is no pinned message in chat, we should
        // mark join message as successful
        if (pinnedMessage == null)
            return true;

        StringBuilder userArray = new StringBuilder("");
        for (User user : users) {
            if (!user.id().equals(botUser.id())) {
                String userReference = user.username();
                if (StringUtils.isEmpty(user.username())) {
                    userReference = user.firstName() + " " + user.lastName();
                }

                if (!StringUtils.isEmpty(userReference.trim())) {
                    if (userArray.length() >  0) userArray.append(", ");
                    userArray.append(userReference);
                }
            }
        }

        String text = userArray.toString().trim() + "\n" +
                "Добрый день. Ознакомьтесь, пожалуйста, с правилами чата.";

        SendMessage sendMessage = new SendMessage(chatId, text).replyToMessageId(pinnedMessage.messageId());
        telegramBot.execute(sendMessage);

        return true;
    }
}
