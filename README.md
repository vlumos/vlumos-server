# vlumos-server

-----------------------------------------------------
basic server (0.1) for vlumos chat app
-----------------------------------------------------

*Please be aware that there are still many things to improve here. This is just an initial basic release.*

It's written in Kotlin at the moment and compiles to FatJar with shadowJar and currently uses port 8100.
Connects to MongoDB.


Uses the following environment variables: 
------------------------------------------------

checkNegTimeout
 - checks if key out of date in milliseconds (eg -3000 = -3 seconds off server time) 
 - found that sometime app and server times are not synced so negative time was needed

checkPosTimeout
 - checks if key out of date in milliseconds (eg 3000 = 3 seconds)

connectionRead
 - read connection string to mongoDB -> mongodb://user:password@ip:port/admin

connectionWrite
 - write connection string to mongoDB -> mongodb://user:password@ip:port/admin

privateKeyString
 - private key used by server

publicKeyString
 - public key used by server

storeMessages
 - true / false (keep downloaded messages on server?)
