package org.jboss.as.console.client.shared.subsys.messaging.connections;

import com.google.gwt.event.shared.EventBus;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Place;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.PlaceRequest;
import com.gwtplatform.mvp.client.proxy.Proxy;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.shared.BeanFactory;
import org.jboss.as.console.client.shared.dispatch.DispatchAsync;
import org.jboss.as.console.client.shared.dispatch.impl.DMRAction;
import org.jboss.as.console.client.shared.dispatch.impl.DMRResponse;
import org.jboss.as.console.client.shared.properties.PropertyRecord;
import org.jboss.as.console.client.shared.subsys.Baseadress;
import org.jboss.as.console.client.shared.subsys.RevealStrategy;
import org.jboss.as.console.client.shared.subsys.messaging.CommonMsgPresenter;
import org.jboss.as.console.client.shared.subsys.messaging.LoadHornetQServersCmd;
import org.jboss.as.console.client.shared.subsys.messaging.model.Acceptor;
import org.jboss.as.console.client.shared.subsys.messaging.model.AcceptorType;
import org.jboss.as.console.client.shared.subsys.messaging.model.MessagingProvider;
import org.jboss.as.console.client.widgets.forms.ApplicationMetaData;
import org.jboss.as.console.client.widgets.forms.EntityAdapter;
import org.jboss.as.console.spi.Subsystem;
import org.jboss.ballroom.client.widgets.window.DefaultWindow;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.jboss.dmr.client.ModelDescriptionConstants.*;

/**
 * @author Heiko Braun
 * @date 4/2/12
 */
public class MsgConnectionsPresenter extends Presenter<MsgConnectionsPresenter.MyView, MsgConnectionsPresenter.MyProxy>
        implements CommonMsgPresenter {

    private final PlaceManager placeManager;
    private DispatchAsync dispatcher;
    private BeanFactory factory;
    private MessagingProvider providerEntity;
    private DefaultWindow window = null;
    private RevealStrategy revealStrategy;
    private ApplicationMetaData metaData;
    private String currentServer = null;
    private EntityAdapter<Acceptor> acceptorAdapter;

    @Override
    public PlaceManager getPlaceManager() {
        return placeManager;
    }

    public void closeDialogue() {
        window.hide();
    }


    @ProxyCodeSplit
    @NameToken(NameTokens.MsgConnectionsPresenter)
    @Subsystem(name="Connections", group = "Messaging", key="messaging")
    public interface MyProxy extends Proxy<MsgConnectionsPresenter>, Place {
    }

    public interface MyView extends View {
        void setPresenter(MsgConnectionsPresenter presenter);
        void setSelectedProvider(String selectedProvider);
        void setProvider(List<String> provider);

        void setGenericAcceptors(List<Acceptor> genericAcceptors);

        void setRemoteAcceptors(List<Acceptor> remote);

        void setInvmAcceptors(List<Acceptor> invm);
    }

    @Inject
    public MsgConnectionsPresenter( EventBus eventBus, MyView view, MyProxy proxy,
                                    PlaceManager placeManager, DispatchAsync dispatcher,
                                    BeanFactory factory, RevealStrategy revealStrategy,
                                    ApplicationMetaData propertyMetaData) {
        super(eventBus, view, proxy);

        this.placeManager = placeManager;
        this.dispatcher = dispatcher;
        this.factory = factory;
        this.revealStrategy = revealStrategy;
        this.metaData = propertyMetaData;

        acceptorAdapter = new EntityAdapter<Acceptor>(Acceptor.class, metaData);
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
    }

    @Override
    public void prepareFromRequest(PlaceRequest request) {
        currentServer = request.getParameter("name", null);
    }

    @Override
    protected void onReset() {
        super.onReset();

        loadProvider();
    }

    public void loadDetails(String selectedProvider) {
        loadAcceptors();
    }

    private void loadProvider() {
        new LoadHornetQServersCmd(dispatcher).execute(
                new AsyncCallback<List<String>>() {
                    @Override
                    public void onFailure(Throwable caught) {
                        Console.error("Failed to load messaging server names", caught.getMessage());
                    }

                    @Override
                    public void onSuccess(List<String> result) {

                        getView().setProvider(result);
                        getView().setSelectedProvider(currentServer);
                    }
                }
        );

    }

    public void loadAcceptors() {

        ModelNode operation = new ModelNode();
        operation.get(ADDRESS).setEmptyList();
        operation.get(OP).set(COMPOSITE);

        List<ModelNode> steps = new ArrayList<ModelNode>();

        ModelNode generic = new ModelNode();
        generic.get(ADDRESS).set(Baseadress.get());
        generic.get(ADDRESS).add("subsystem", "messaging");
        generic.get(ADDRESS).add("hornetq-server", getCurrentServer());
        generic.get(OP).set(READ_CHILDREN_RESOURCES_OPERATION);
        generic.get(CHILD_TYPE).set("acceptor");
        generic.get(RECURSIVE).set(true);
        steps.add(generic);


        ModelNode remote = new ModelNode();
        remote.get(ADDRESS).set(Baseadress.get());
        remote.get(ADDRESS).add("subsystem", "messaging");
        remote.get(ADDRESS).add("hornetq-server", getCurrentServer());
        remote.get(OP).set(READ_CHILDREN_RESOURCES_OPERATION);
        remote.get(CHILD_TYPE).set("remote-acceptor");
        remote.get(RECURSIVE).set(true);
        steps.add(remote);

        ModelNode invm = new ModelNode();
        invm.get(ADDRESS).set(Baseadress.get());
        invm.get(ADDRESS).add("subsystem", "messaging");
        invm.get(ADDRESS).add("hornetq-server", getCurrentServer());
        invm.get(OP).set(READ_CHILDREN_RESOURCES_OPERATION);
        invm.get(CHILD_TYPE).set("in-vm-acceptor");
        invm.get(RECURSIVE).set(true);
        steps.add(invm);

        operation.get(STEPS).set(steps);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();

                if(response.isFailure())
                {
                    Console.error(Console.MESSAGES.failed("Loading acceptors " + getCurrentServer()), response.getFailureDescription());
                }
                else
                {
                    List<Acceptor> generic = parseAcceptors(response.get(RESULT).get("step-1"), AcceptorType.GENERIC);
                    getView().setGenericAcceptors(generic);

                    List<Acceptor> remote = parseAcceptors(response.get(RESULT).get("step-2"), AcceptorType.REMOTE);
                    getView().setRemoteAcceptors(remote);

                    List<Acceptor> invm = parseAcceptors(response.get(RESULT).get("step-3"), AcceptorType.INVM);
                    getView().setInvmAcceptors(invm);
                }
            }
        });

    }

    private List<Acceptor> parseAcceptors(ModelNode step, AcceptorType type) {

        List<Property> generic = step.get(RESULT).asPropertyList();
        List<Acceptor> genericAcceptors = new ArrayList<Acceptor>();
        for(Property prop : generic)
        {
            ModelNode acceptor = prop.getValue();
            Acceptor model = acceptorAdapter.fromDMR(acceptor);
            model.setName(prop.getName());
            model.setType(type);

            if(acceptor.hasDefined("param"))
            {
                List<PropertyRecord> param = parseProperties(acceptor.get("param").asPropertyList());
                model.setParameter(param);
            }
            else
            {
                model.setParameter(Collections.EMPTY_LIST);
            }

            genericAcceptors.add(model);
        }
        return genericAcceptors;
    }

    private List<PropertyRecord> parseProperties(List<Property> properties) {
        List<PropertyRecord> records = new ArrayList<PropertyRecord>(properties.size());
        for(Property prop : properties)
        {
            String name = prop.getName();
            String value = prop.getValue().asObject().get("value").asString();
            PropertyRecord propertyRecord = factory.property().as();
            propertyRecord.setKey(name);
            propertyRecord.setValue(value);
            records.add(propertyRecord);
        }

        return records;
    }

    public String getCurrentServer() {
        return currentServer;
    }

    @Override
    protected void revealInParent() {
        revealStrategy.revealInParent(this);
    }

    public void launchNewAcceptorWizard(final AcceptorType type) {
        loadSocketBindings(new AsyncCallback<List<String>>() {
            @Override
            public void onFailure(Throwable throwable) {
                Console.error("Failed to load socket bindings", throwable.getMessage());
            }

            @Override
            public void onSuccess(List<String> names) {
                window = new DefaultWindow(Console.MESSAGES.createTitle("Acceptor"));
                window.setWidth(480);
                window.setHeight(360);

                window.trapWidget(new NewAcceptorWizard(MsgConnectionsPresenter.this, names, type).asWidget());


                window.setGlassEnabled(true);
                window.center();
            }
        });
    }

    public void onDeleteAcceptor(final Acceptor entity) {
        ModelNode address = Baseadress.get();
        address.add("subsystem", "messaging");
        address.add("hornetq-server", getCurrentServer());
        address.add(entity.getType().getResource(), entity.getName());

        ModelNode operation = acceptorAdapter.fromEntity(entity);
        operation.get(ADDRESS).set(address);
        operation.get(OP).set(REMOVE);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();

                if(response.isFailure())
                    Console.error(Console.MESSAGES.deletionFailed("Acceptor " + entity.getName()), response.getFailureDescription());
                else
                    Console.info(Console.MESSAGES.deleted("Acceptor " + entity.getName()));

                loadAcceptors();
            }
        });
    }

    public void onSaveAcceptor(Acceptor entity, Map<String, Object> changeset) {

    }

    public void loadSocketBindings(AsyncCallback<List<String>> callback) {

        // TODO
        callback.onSuccess(Collections.EMPTY_LIST);
    }

    public void onCreateAcceptor(final Acceptor entity) {
        window.hide();

        ModelNode address = Baseadress.get();
        address.add("subsystem", "messaging");
        address.add("hornetq-server", getCurrentServer());
        address.add(entity.getType().getResource(), entity.getName());

        ModelNode operation = acceptorAdapter.fromEntity(entity);
        operation.get(ADDRESS).set(address);
        operation.get(OP).set(ADD);

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();

                if(response.isFailure())
                    Console.error(Console.MESSAGES.addingFailed("Acceptor " + entity.getName()), response.getFailureDescription());
                else
                    Console.info(Console.MESSAGES.added("Acceptor " + entity.getName()));

                loadAcceptors();
            }
        });
    }

}
