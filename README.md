# vlumos-server

basic server (0.1) for vlumos chat app

-----------------------------------------------------
There are still many things to improve in the server
-----------------------------------------------------

Can compile to FatJar with shadowjar and currently uses port 8100.
Connects to MongoDB.


------ Uses the following environment variables: ------

checkNegTimeout
 - checks if key out of date (found that sometime app and server times are not synced so negative time was needed) 

checkPosTimeout
 - checks if key out of date

connectionRead
 - read connection string to mongoDB -> mongodb://user:password@ip:port/admin

connectionWrite
 - write connection string to mongoDB -> mongodb://user:password@ip:port/admin

privateKeyString
 - private key used by server

publicKeyString
 - public key used by serve

storeMessages
 - true / false (keep download messages on server?)



