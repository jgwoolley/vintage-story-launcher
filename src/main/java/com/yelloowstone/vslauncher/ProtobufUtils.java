package com.yelloowstone.vslauncher;

import java.io.IOException;
import java.io.InputStream;

import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.DynamicMessage;

public class ProtobufUtils {
	
	public static Descriptors.FileDescriptor getFileDescriptor() throws IOException, DescriptorValidationException {
		// 1. Load the descriptor set from the file
        final InputStream is = ProtobufUtils.class.getResourceAsStream("/vintagestory.desc");
        if (is == null) {
            // This is likely why you get the Index error later
            throw new RuntimeException("Resource NOT found at: /vintagestory.desc. " +
                    "Check if the file exists in src/main/resources/audio/ and run a 'clean build'.");
        }
        
        final FileDescriptorSet set = FileDescriptorSet.parseFrom(is);
        
        // 2. Build the Descriptors from the set
        // Note: For complex schemas with dependencies, you may need to iterate through files
        return Descriptors.FileDescriptor.buildFrom(
                set.getFile(0), new Descriptors.FileDescriptor[]{}
        );
	}
	
	public static DynamicMessage parseMessage(byte[] incomingData, String fullMessageName) throws IOException, Descriptors.DescriptorValidationException {

        final Descriptors.FileDescriptor fileDesc = getFileDescriptor();

        // 3. Find the specific message type (e.g., "Vintagestory.Packet")
        final Descriptors.Descriptor messageDescriptor = fileDesc.findMessageTypeByName(fullMessageName);
        
        if (messageDescriptor == null) {
            throw new IllegalArgumentException("Message type not found: " + fullMessageName);
        }

        // 4. Parse the raw bytes into a DynamicMessage
        return DynamicMessage.parseFrom(messageDescriptor, incomingData);
    }
	
	public static byte[] createVec2i(int x, int y) throws IOException, DescriptorValidationException {
        final Descriptors.FileDescriptor fileDesc = getFileDescriptor();
		
	    // 1. Get the descriptor for Vec2i from your loaded file descriptor
	    // (Assuming 'fileDesc' is your cached FileDescriptor object)
	    Descriptors.Descriptor vec2iDesc = fileDesc.findMessageTypeByName("Vec2i");

	    // 2. Create a builder for this message type
	    DynamicMessage vec2iMessage = DynamicMessage.newBuilder(vec2iDesc)
	            .setField(vec2iDesc.findFieldByName("X"), x)
	            .setField(vec2iDesc.findFieldByName("Y"), y)
	            .build();
	    
	    // 3. Serialize to byte array
	    return vec2iMessage.toByteArray();
	}
	
	public static void main(String args[]) throws IOException, DescriptorValidationException {
		byte[] rawValue = createVec2i(3, 0);
		System.out.println(rawValue);
		DynamicMessage message = parseMessage(rawValue, "Vec2i");
		
		Descriptors.Descriptor descriptor = message.getDescriptorForType();
		
		Descriptors.FieldDescriptor xField = descriptor.findFieldByName("X");
	    Descriptors.FieldDescriptor yField = descriptor.findFieldByName("Y");
	    
	    int x = (int) message.getField(xField);
	    int y = (int) message.getField(yField);
		
		System.out.println("Test" + x + " " + y);

	}
}
