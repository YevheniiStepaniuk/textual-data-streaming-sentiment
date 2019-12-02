const Telegraf = require('telegraf');
const kafka = require('kafka-node');
const createLogger = require('./logger');
const uuidv1 = require('uuid/v1');

const logger = createLogger();

logger.info('Telegram bot is running .... Please stay on line');

const kafkaClientOptions = { sessionTimeout: 100, spinDelay: 100, retries: 2 };
const kafkaClient = new kafka.Client(
  process.env.KAFKA_ZOOKEEPER_CONNECT,
  'sentiment-producer-client',
  kafkaClientOptions
);
const kafkaProducer = new kafka.HighLevelProducer(kafkaClient);
kafkaClient.on('error', error => logger.error('Kafka client error:', error));
kafkaProducer.on('error', error =>
  logger.error('Kafka producer error:', error)
);

const bot = new Telegraf(process.env.TELEGRAM_BOT_KEY);
bot.use((ctx, next) => {
  if (ctx.updateType === 'message') {
    logger.info(
      `${ctx.updateType} => user: (${ctx.chat.username} : ${ctx.chat.id}) chatId: ${ctx.chat.id} message: ${ctx.message.text}`
    );
  }

  next();
});

bot.on('message', ctx => {
  const { chat, message } = ctx;
  const { text, date } = message;
  const { first_name, last_name, username } = message.from;

  let { title, chat_name } = chat;

  const userFullName = `${first_name} ${last_name}`;
  title = title || userFullName;
  chat_name = chat_name || username;

  logger.info('New message, working...');
  logger.info(JSON.stringify(message));

  const payload = [
    {
      topic: process.env.KAFKA_TOPIC,
      key: uuidv1(),
      messages: [
        JSON.stringify({
          message: text,
          user_full_name: userFullName,
          from_username: username,
          date: date,
          chat_title: title,
          chat_name: chat_name,
          chat_id: chat.id
        })
      ],
      attributes: 1
    }
  ];

  kafkaProducer.send(payload, function(error, result) {
    logger.info('Sent payload to Kafka:', payload);

    if (error) {
      logger.error('Sending payload failed:', error);
    } else {
      logger.info('Sending payload result:', result);
    }
  });
});

bot.startPolling();
