import discord
import time
import asyncio
import os

try:
    DISCORD_TOKEN = os.environ["DISCORD_TOKEN"]
except KeyError:
    DISCORD_TOKEN = "Token not available!"
    #logger.info("Token not available!")
    #raise


if __name__ == "__main__":
    token = DISCORD_TOKEN

    intents = discord.Intents.default()
    intents.message_content = True

    last_messages = 5
    client = discord.Client(intents=intents)



    @client.event
    async def on_ready():
        print(f'We have logged in as {client.user}')
    
    @client.event
    async def on_ready():
        await asyncio.sleep(60)
        await quit()
        
    @client.event
    async def on_ready():
        channel = client.get_channel(1083995331844378637)
        messages = [message.content async for message in channel.history(limit=last_messages)]

        f = open("test.txt", "w")
        for items in messages:
                f.write("%s\n" % item)
        f.close()

    
    @client.event
    async def on_message(message):
        channels = ["general"]

        if str(message.channel) in channels:
            if message.content == "!history":
                messages = [message.content async for message in message.channel.history(limit=last_messages)]

                await message.channel.send(f"Last {last_messages} messages:")
                await message.channel.send(messages)

    client.run(token)
