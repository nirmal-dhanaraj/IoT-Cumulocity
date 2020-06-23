package c8y.example;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cumulocity.microservice.autoconfigure.MicroserviceApplication;
import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;
import com.cumulocity.microservice.settings.service.MicroserviceSettingsService;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionAddedEvent;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionsInitializedEvent;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.microservice.subscription.service.impl.MicroserviceSubscriptionsServiceImpl.MicroserviceChangedListener;
import com.cumulocity.model.ID;
import com.cumulocity.model.authentication.AuthenticationMethod;
import com.cumulocity.model.authentication.CumulocityCredentials;
import com.cumulocity.model.authentication.CumulocityCredentials.CumulocityCredentialsVisitor;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.event.EventRepresentation;
import com.cumulocity.rest.representation.identity.ExternalIDRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.ClientConfiguration;
import com.cumulocity.sdk.client.Platform;
import com.cumulocity.sdk.client.PlatformParameters;
import com.cumulocity.sdk.client.SDKException;
import com.cumulocity.sdk.client.alarm.AlarmApi;
import com.cumulocity.sdk.client.cep.notification.CepCustomNotificationsSubscriber;
import com.cumulocity.sdk.client.event.EventApi;
import com.cumulocity.sdk.client.event.EventCollection;
import com.cumulocity.sdk.client.event.PagedEventCollectionRepresentation;
import com.cumulocity.sdk.client.identity.IdentityApi;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.inventory.ManagedObjectCollection;
import com.cumulocity.sdk.client.measurement.MeasurementApi;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.cumulocity.model.authentication.CumulocityCredentials;

import c8y.IsDevice;
import javassist.expr.NewArray;
import net.minidev.json.JSONObject;


@MicroserviceApplication
@RestController
public class App{
		
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @RequestMapping("hello")
    public String greeting(@RequestParam(value = "name", defaultValue = "world") String name) {
        return "hello " + name + "!";
    }

    // You need the inventory API to handle managed objects e.g. creation. You will find this class within the C8Y java client library.
    private final InventoryApi inventoryApi;
    // you need the identity API to handle the external ID e.g. IMEI of a managed object. You will find this class within the C8Y java client library.
    private final IdentityApi identityApi;
    
    // you need the measurement API to handle measurements. You will find this class within the C8Y java client library.
    private final MeasurementApi measurementApi;
    
    // you need the alarm API to handle measurements.
    private final AlarmApi alarmApi;
    
    // you need the event API to handle measurements.
    private final EventApi eventApi;
    
    @Autowired
	private MicroserviceSubscriptionsService subscriptionsService;
	
	@Autowired
	private Platform c8yPlatform;
	
        
    // To access the tenant options
    private final MicroserviceSettingsService microserviceSettingsService;
    
    @Autowired
    public App( InventoryApi inventoryApi, 
    			IdentityApi identityApi, 
    			MicroserviceSubscriptionsService subscriptionService,
    			MeasurementApi measurementApi,
    			MicroserviceSettingsService microserviceSettingsService,
    			AlarmApi alarmApi,
    			EventApi eventApi) {
        this.inventoryApi = inventoryApi;
        this.identityApi = identityApi;
        this.subscriptionsService = subscriptionService;
        this.measurementApi = measurementApi;
        this.microserviceSettingsService = microserviceSettingsService;
        this.alarmApi = alarmApi;
        this.eventApi = eventApi;
    }
    
    // Create every x sec a new measurement
   // @Scheduled(fixedRate = 5000)
    @Scheduled(initialDelay=10000, fixedDelay=5000)
    public void startThread() {
    	System.out.println("Inside StartThread1 --------------");
    	subscriptionsService.runForEachTenant(new Runnable() {
			@Override
			public void run() {
		    	try {
		    		System.out.println("Inside StartThread2--------------");
		    		checkDoorStatus();	    		
				} catch (Exception e) {
					e.printStackTrace();
				} 
			}
		});
    }
        
    // Create a new managed object + external ID (if not existing)  
    private ManagedObjectRepresentation resolveManagedObject() {
       	
    	try {
        	// check if managed object is existing. create a new one if the managed object is not existing
    		ExternalIDRepresentation externalIDRepresentation = identityApi.getExternalId(new ID("c8y_Serial", "microservice-part4b_externalId"));
			return externalIDRepresentation.getManagedObject();    	    	

    	} catch(SDKException e) {
    		    		
    		// create a new managed object
			ManagedObjectRepresentation newManagedObject = new ManagedObjectRepresentation();
	    	newManagedObject.setName("microservice-part4b");
	    	newManagedObject.setType("microservice-part4b");
	    	newManagedObject.set(new IsDevice());	    	
	    	ManagedObjectRepresentation createdManagedObject = inventoryApi.create(newManagedObject);
	    	
	    	// create an external id and add the external id to an existing managed object
	    	ExternalIDRepresentation externalIDRepresentation = new ExternalIDRepresentation();
	    	// Definition of the external id
	    	externalIDRepresentation.setExternalId("microservice-part4b_externalId");
	    	// Assign the external id to an existing managed object
	    	externalIDRepresentation.setManagedObject(createdManagedObject);
	    	// Definition of the serial
	    	externalIDRepresentation.setType("c8y_Serial");
	    	// Creation of the external id
	    	identityApi.create(externalIDRepresentation);
	    	
	    	return createdManagedObject;
    	}
    }
    
    @EventListener(MicroserviceSubscriptionAddedEvent.class)
    public void listenEvent(MicroserviceSubscriptionAddedEvent e) {
    	
    	//System.out.println("Inside Listener"+eventList);  
    	//System.out.println("Inside Listener eventList Size------"+eventList.size());
    	
    }
    
    @EventListener
	public void onSubscriptionsInitialized(MicroserviceSubscriptionsInitializedEvent event) {
    	System.out.println("Subscriptions have been initialized on application startup");
		listSubscriptions();
		runBusinessLogicForAllTenants();
		
		try {
			runBusinessLogicOutOfContext();			
		} catch (Exception e) {
			System.out.println("As expected: error, not in any tenant scope " + e.getMessage()); // results in "java.lang.IllegalStateException: Not within any context!"
		}
	}
    
    private void listSubscriptions() {
		if (subscriptionsService.getAll().isEmpty()) {
			System.out.println("No tenants are subscribed to this application");
		} else {
			for (MicroserviceCredentials microserviceCredentials : subscriptionsService.getAll()) {
				System.out.println(String.format("The tenant %s is subscribed to this application ",  microserviceCredentials.getTenant()));
			}
		}		
	};
	
	private void runBusinessLogicForAllTenants() {
		subscriptionsService.runForEachTenant( ()->{
			/*
			 * runForEachTenant() works some magic behind the scenes. Code that is wrapped in  
			 * runForEachTenant() will actually use a different instance of Platform (and InventoryApi) 
			 * for each tenant. Under the hood, this is implemented using Spring's custom
			 * scopes functionality (see https://www.baeldung.com/spring-custom-scope)
			 * 
			 */
			String tenant= subscriptionsService.getTenant();		 
			EventApi tenantEventApi = c8yPlatform.getEventApi();
			EventCollection eventCollection = tenantEventApi.getEvents();
			Iterator<EventRepresentation> itor = eventCollection.get().elements(1).iterator();
			while (itor.hasNext()) {
				EventRepresentation eventRepresentation = itor.next();
                try {
                	System.out.println(String.format("Fteched Event object with id %s from tenant %s: %s",
                			eventRepresentation.getId().getValue(), 
							tenant,
							new ObjectMapper().writeValueAsString(eventRepresentation)));
				} catch (JsonProcessingException e) {
					System.out.println("Error writing JSON string"+ e);
				}
            }
		});
	}
	
	/**
	 * This will throw an exception unless it is wrapped in one of:  
	 * 
	 * MicroserviceSubscriptionsService.runForEachTenant()
	 * MicroserviceSubscriptionsService.runForTenant() 
	 * MicroserviceSubscriptionsService.callForTenant()
	 * ...
	 * 
	 * Or wrapped in: 
	 * 
	 * ContextService.runWithinContext(MicroserviceCredentials c,  Runnable task)
	 * ...
	 * 
	 */
	private void runBusinessLogicOutOfContext() {
		EventApi eventApi = c8yPlatform.getEventApi();
		EventCollection eventCollection = eventApi.getEvents();
		Iterator<EventRepresentation> itor = eventCollection.get().elements(1).iterator();
		while (itor.hasNext()) {
			EventRepresentation eventRepresentation = itor.next();
            try {
            	System.out.println(String.format("Fteched managed object with id %s : %s",
            			eventRepresentation.getId().getValue(),						
						new ObjectMapper().writeValueAsString(eventRepresentation)));
			} catch (JsonProcessingException e) {
				System.out.println("Error writing JSON string"+ e);
			}
        }	
	}
	
    public void checkDoorStatus() {    	
    	// Simulator for opening and closing of a door
    	
    	System.out.println("Inside checkDoorStatus 1--------------");
    	Random r = new Random();
    	int i = r.nextInt(100);
    	if(i%2==0) {
    		// door open -> create a new event
    		System.out.println("Inside checkDoorStatus 2--------------");
    		// Managed object representation will give you access to the ID of the managed object
    		ManagedObjectRepresentation managedObjectRepresentation = resolveManagedObject();

    		// Event representation object
    		EventRepresentation eventRepresentation = new EventRepresentation();
    		
    		// set the event properties
    		eventRepresentation.setDateTime(new DateTime());
    		// add event to a managed object
    		eventRepresentation.setSource(managedObjectRepresentation);
    		eventRepresentation.setText("Door open");
    		eventRepresentation.setType("Event_type");
    		System.out.println("Inside checkDoorStatus 3--------------");
    		// create a new event
    		//eventApi.create(eventRepresentation);
    		System.out.println("Inside checkDoorStatus 4--------------");
    	} else {
    		// door closed -> do nothing
    	}
    }

    // get event by id
    @RequestMapping("getEventById")
    public String getEventById(@RequestParam(value = "eventId") String eventId) {
		if(eventId.length()>=1) {
			try {
				// Use GId to transform the given id to a global c8y id
				EventRepresentation eventRepresentation = eventApi.getEvent(GId.asGId(eventId));
				
				return eventRepresentation.toJSON();
			} catch(Exception e) {
				return "Event with the id "+eventId+" does not exist.";
			}
		}
		return "Insert a valid event id.";
    }

    // get all events
    @RequestMapping("getAllEvents")
    public List<EventRepresentation> getAllEvents() {
    	
    	// To get access to event collection representation
    	EventCollection eventCollection = eventApi.getEvents();
    	
    	// To get access to e.g. all event pages
    	PagedEventCollectionRepresentation pagedEventCollectionRepresentation = eventCollection.get();   
    	
    	
    	
    	// Representation of a series of event elements. Get all pages.
    	Iterable<EventRepresentation> iterable = pagedEventCollectionRepresentation.allPages();
    	
    	// Usage of google guava to create an event list
    	List<EventRepresentation> eventRepresentationList = Lists.newArrayList(iterable);
    	
    	//eventRepresentationList.stream().filter(er -> er.getType().startsWith("SLA Reporting"))
    	
    	List<EventRepresentation> eventRepresentationListbyType = eventRepresentationList.stream().filter(er -> er.getType().startsWith("SLA Reporting")).collect(Collectors.toList());

    	Iterator<EventRepresentation> iterator = eventRepresentationListbyType.iterator();
     	System.out.println("TEST Events");
     	System.out.println();
        while(iterator.hasNext()) {
        Map<String, Object> attributes = new HashMap<>();
        attributes = iterator.next().getAttrs();
       // Object c = attributes.get("event_data");   
       // System.out.println(c.toString());
         
           System.out.println(iterator.next());
        }
   
    //	System.out.println(eventRepresentationList);
    //	System.out.println(eventRepresentationListbyType);
    	return eventRepresentationListbyType;
    

	}
}