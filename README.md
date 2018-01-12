# AlexaTemperatureControl
This project was backed by the University of Waterloo's Centre for Pattern Analysis and Machine Intelligence (CPAMI) and Amazon's Alexa Fund Fellowship Program

https://uwaterloo.ca/stories/amazon-partners-waterloo-support-ai-research

This IoT project is an intelligent system that allows for temperature control of different rooms in a home by interacting with Amazon's Alexa device. The central focus of this system is its Fuzzy logic controller which allows for voice commands that contain ambiguous temperature related terms (e.g. "Make kitchen warm" or "Make it cold in bedroom 1"). These "fuzzy" voice commands are in contrast to the typical voice commands implemented by other temperature control systems (e.g. "Make it 24 degrees Celsius in the kitchen"). To make this possible a Fuzzy Logic inference system based on the Mamdani method was designed and implemented. In addition, a miniature model home was created with fans and heaters interfaced to a Raspberry Pi 3, which made the connection to Alexa possible.  

Technologies used: Java, Python, AWS Lambda, AWS IoT, Amazon Alexa, Raspberry Pi3
