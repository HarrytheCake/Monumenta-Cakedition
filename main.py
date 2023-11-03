import discord
import time
import asyncio
import os
import textwrap

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

    last_messages = 10
    client = discord.Client(intents=intents)



    @client.event
    async def on_ready():
        print(f'We have logged in as {client.user}')
    
    @client.event
    async def on_ready():
        await asyncio.sleep(30)
        await quit()
        
    @client.event
    async def on_ready():
        a_channel = client.get_channel(1168874574134378618)
        a_messages = [message.content async for message in a_channel.history(limit=last_messages)]

        fa = open("announcement.txt", "w")
        await a_messages.reverse()
        for items in a_messages:
                fa.write("%s\n" % textwrap.fill(items,128))
        fa.close()
        
        c_channel = client.get_channel(1168874628354158642)
        c_messages = [message.content async for message in c_channel.history(limit=last_messages)]

        fc = open("change_logs.txt", "w")
        await c_messages.reverse() 
        for items in c_messages:
                fc.write("%s\n" % textwrap.fill(items,128))
        fc.close()
        
        await quit()
    
    @client.event
    async def on_message(message):
        channels = ["general"]

        if str(message.channel) in channels:
            if message.content == "!history":
                messages = [message.content async for message in message.channel.history(limit=last_messages)]

                await message.channel.send(f"Last {last_messages} messages:")
                await message.channel.send(messages)

    client.run(token)
