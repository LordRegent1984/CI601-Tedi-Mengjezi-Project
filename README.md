# CI601-Tedi-Mengjezi-Project
This repo contains the code for a designed and developed slot engine with the goal of ensuring fair and random bets by utilising true random seed generation and a HMAC-DRBG
Code was developed using JDK 22

Database configs are stored in a separate file attached to the report, properties placeholders need to be replaced with said configs, without it the main class and front end will not work - tests will still function as expected

The main class is an executable class built to test the initial flow and to configure the HMAC-DRBG
The two tests are designed to test if the slot is random (Chi-Squared) and fair (Monte Carlo)

To launch the front end application follow the below steps:
Open the Maven tab on the left of the IDE

Click Lifecycle
Run clean
Run package
Click Plugins
Click cargo
Run cargo: run
Open desired search engine
Enter this URL: http://localhost:8080/game?accountID=TestUser1&gameID=1001
Replace the user with the accounts listed below and keep the gameID as 1001

I created multiple users for testing the overall functionality of the slot
3 accounts that have a large balance:
TestUser1
TestUser2
TestUser3

3 accounts with a low balance to test no balance during spin:
TestUser4
TestUser5
TestUser6

1 account with no balance to test spinning from launch:
TestUser7

To see previous bets placed enter below URL with accountID used when front end application is running
http://localhost:8080/bethistory?accountID=TestUser1