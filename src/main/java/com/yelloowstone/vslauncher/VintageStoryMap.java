package com.yelloowstone.vslauncher;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Properties;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.sqlite.SQLiteConfig;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;

public class VintageStoryMap {
	private final File file;

	public VintageStoryMap(File file) {
		this.file = file;
	}
	
	public File getFile() {
		return file;
	}
	
	public Stream<VintageStoryMapPosition> getPositions() {
		final SQLiteConfig config = new SQLiteConfig();
	    config.setReadOnly(true);
	    final Properties props = config.toProperties();
	    props.setProperty("immutable", "true");
	    
	    final String url = "jdbc:sqlite:" + file.toString();
	    
	    try {
			final Connection connection = DriverManager.getConnection(url, props);
			final Statement statement = connection.createStatement();
			final ResultSet rs = statement.executeQuery("SELECT * FROM mappiece");

			final FileDescriptor fileDescriptor = ProtobufUtils.getFileDescriptor();
			final Descriptor descriptor = fileDescriptor.findMessageTypeByName("MapPiece");
			
			final FieldDescriptor pixelsField = descriptor.findFieldByName("pixels");
			final FieldDescriptor versionField = descriptor.findFieldByName("version");
			final FieldDescriptor lastModifiedField = descriptor.findFieldByName("lastModified");
			
			// Convert ResultSet to an Iterator
		    final Iterator<VintageStoryMapPosition> iterator = new Iterator<>() {
		        @Override
		        public boolean hasNext() {
		            try {
		                return rs.next();
		            } catch (SQLException e) {
		                throw new RuntimeException(e);
		            }
		        }

		        @Override
		        public VintageStoryMapPosition next() {		        	    	
		            try {
		            	final int rawPosition = rs.getInt("position");
		            	final byte[] rawData = rs.getBytes("data");
		            	
		            	final DynamicMessage message =  DynamicMessage.parseFrom(descriptor, rawData);

		            	final ByteString rawPixles =  (ByteString) message.getField(pixelsField);
		            	final byte[] pixles =  rawPixles.toByteArray();
		            	final int version =  (int) message.getField(versionField);
		            	final long lastModified =  (long) message.getField(lastModifiedField);
		            			            	
		                return new VintageStoryMapPosition() {
							@Override
							public int getRawPosition() {
								return rawPosition;
							}

							@Override
							public byte[] getRawData() {
								return rawData;
							}

							@Override
							public byte[] getPixles() {
								return pixles;
							}

							@Override
							public int getVersion() {
								return version;
							}

							@Override
							public long getLastModified() {
								return lastModified;
							}
		                	
		                };
		            } catch (Exception e) {
		                throw new RuntimeException(e);
		            }
		        }
		    };

		    // Convert Iterator to Stream
		    return StreamSupport.stream(
		        Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), 
		        false
		    ).onClose(() -> {
		        // Crucial: Close resources when the stream is closed
		        try { rs.close(); statement.close(); } catch (SQLException e) { /* log error */ }
		    });
			
		} catch (Exception e) {
			return Stream.of();
		}
	}
	
	
}
