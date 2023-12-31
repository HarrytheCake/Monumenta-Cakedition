import discord
import time
import asyncio
import os
import textwrap
import re

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

    def markdown(string):
        t_string = string.replace('- ','• ')

        string_list = t_string.split("**")
        temp_string = ""
        for i in range(len(string_list)):
                if i == 0:
                        temp_string += string_list[i]
                elif i%2 != 0:
                        temp_string += ("&b"+string_list[i]+"&r")
                else :
                        temp_string += string_list[i]
        t_string = temp_string

        string_list = t_string.split("*")
        temp_string = ""
        for i in range(len(string_list)):
                if i == 0:
                        temp_string += string_list[i]
                elif i%2 != 0:
                        temp_string += ("&o"+string_list[i]+"&r")
                else :
                        temp_string += string_list[i]
        t_string = temp_string

        string_list = t_string.split("__")
        temp_string = ""
        for i in range(len(string_list)):
                if i == 0:
                        temp_string += string_list[i]
                elif i%2 != 0:
                        temp_string += ("&n"+string_list[i]+"&r")
                else :
                        temp_string += string_list[i]
        t_string = temp_string

        return t_string

    @client.event
    async def on_ready():
        print(f'We have logged in as {client.user}')
    
    @client.event
    async def on_ready():
        await asyncio.sleep(20)
        await quit()
        
    @client.event
    async def on_ready():
        a_channel = client.get_channel(1168874574134378618)
        a_messages = [message.content async for message in a_channel.history(limit=last_messages)]

        a_messages.reverse()
        a_string = ''.join(map(str,a_messages))
        a_string = markdown(a_string)
        a_string = a_string.replace("&b","\n&b")
        a_messages = a_string.splitlines()
        fa = open("announcement.txt", "w")
        for items in a_messages:
                for item in textwrap.wrap(items,64):
                    fa.write("%s\n" % item)
        fa.close()
        
        c_channel = client.get_channel(1168874628354158642)
        c_messages = [message.content async for message in c_channel.history(limit=last_messages)]

        c_messages.reverse()
        c_string = ''.join(map(str,c_messages))
        c_string = markdown(c_string)
        c_string = c_string.replace("&b","\n&b")
        c_messages = c_string.splitlines()

        fc = open("change_logs.txt", "w")
        for items in c_messages:
                for item in textwrap.wrap(items,64):
                    fc.write("%s\n" % item)
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
