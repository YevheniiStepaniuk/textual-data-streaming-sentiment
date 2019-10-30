const winston = require('winston')

module.exports = function createLogger() {
  return winston.createLogger({
    transports: [
      new winston.transports.Console({
        handleExceptions: true,
        format: winston.format.combine(
          winston.format.colorize(),
          winston.format.timestamp({
            format: 'YYYY-MM-DD HH:mm:ss',
          }),
          winston.format.simple()
        ),
        level: 'debug',
      })
    ],
  });
}