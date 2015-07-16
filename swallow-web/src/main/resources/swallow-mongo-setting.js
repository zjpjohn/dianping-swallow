//rs.slaveOk();
db = db.getSiblingDB('swallowwebapplication');

db.swallowwebconsumeridalarmsettingc.update({"consumerId":"default"},{"consumerId": "default","consumerAlarmSetting" : { "sendQpsAlarmSetting" : { "peak" : NumberLong(40), "valley" : NumberLong(1), "fluctuation" : 3 }, "ackQpsAlarmSetting" : { "peak" : NumberLong(40), "valley" : NumberLong(1), "fluctuation" : 3 }, "sendDelay" : NumberLong(3), "ackDelay" : NumberLong(500), "accumulation" : NumberLong(1) }},true,false);
db.swallowwebconsumerserveralarmsettingc.update({"serverId":"defalut"},{"serverId": "default","topicWhiteList" : [ "x" ], "senderAlarmSetting" : { "peak" : NumberLong(1), "valley" : NumberLong(1), "fluctuation" : 20 }, "ackAlarmSetting" : { "peak" : NumberLong(1), "valley" : NumberLong(1), "fluctuation" : 20 }},true,false);
db.swallowwebproducerserveralarmsettingc.update({"serverId":"default"},{"serverId": "default","topicWhiteList" : [ "x" ], "defaultAlarmSetting" : { "peak" : NumberLong(100), "valley" : NumberLong(30), "fluctuation" : 10 }},true,false);
db.swallowwebswallowalarmsettingc.update({"swallowId":"default"},{"swallowId": "default","producerWhiteList" : [ "10.128.121.229" ], "consumerWhiteList" : [ "10.128.121.229" ] },true,false);
db.swallowwebtopicalarmsettingc.update({"topicName":"default"},{"topicName": "default","consumerIdWhiteList" : [ "x" ], "producerAlarmSetting" : { "qpsAlarmSetting" : { "peak" : NumberLong(100), "valley" : NumberLong(1), "fluctuation" : 2 }, "delay" : NumberLong(1) }, "consumerAlarmSetting" : { "sendQpsAlarmSetting" : { "peak" : NumberLong(30), "valley" : NumberLong(5), "fluctuation" : 5 }, "ackQpsAlarmSetting" : { "peak" : NumberLong(30), "valley" : NumberLong(5), "fluctuation" : 5 }, "sendDelay" : NumberLong(30), "ackDelay" : NumberLong(5), "accumulation" : NumberLong(10)}},true,false);

