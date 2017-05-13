jQuery.support.cors = true;

if (!window.console) {
  console = {
    log : function() {}
  };
}

/*
  Base protocol handler. Gets user UUID when subsocket is ready
 */
ProtocolHandler = Base.extend({
  constructor : function(socket, viewModel) {
    var self = this;
    this.viewModel = viewModel;

    this.base();

    this.request = {
      url: "/ws",
      logLevel: 'debug',
      contentType : "application/json",
      closeAsync: true,
      transport: 'websocket',
      fallbackTransport: 'long-polling'
    };

    this.uid = null;

    //TODO: handle protocol failure

    this.request.onOpen = function(response) {
      console.log('Atmosphere connected using ' + response.transport);
      console.log("What is our Atmosphere UID?");
      self.sendCommand({'action': 'getUID'})
    };

    this.request.onReconnect = function(rq, rs) {
      self.socket.info("Reconnecting");
    };

    this.request.onClose = function(rs) {
      console.log("Closing connection")
    };

    this.request.onError = function(rs) {
     
      console.log("Socket Error");
      console.log(rs);
    };


    this.socket = socket;
    this.subSocket = null;
  },

  onMessage: function(rs) {
    var self = this;

    console.log(rs);
    var message = rs.responseBody;
    console.log('ws -> ' + message);

    try {
      var json = jQuery.parseJSON(message);
    } catch (e) {
      console.log('This doesn\'t look like a valid JSON, bro: ', message);
    }

    if (json.uid) {
      self.uid = json.uid;
    }

    return json;
  },

  sendCommand: function(message) {
    var json = JSON.stringify(message);
    console.log('ws <- ' + json);
    this.subSocket.push(json);
  }
});
//-----------------------------------------------------------------------------

TrelloProtocolHandler = ProtocolHandler.extend({
  constructor : function(socket, viewModel) {
    var self = this;
    this.base(socket, viewModel);

    this.request.onMessage = function(rs) {
      var json = self.onMessage(rs);

      if (json.card && json.card.uid != self.uid) {
        var card = json.card;
        $('#' + card.listId).append(
            '<div id="card' +  card.no + '"class="bg-success text-danger">' +
            card.text + ' from User ' + json.card.uid + '</div>'
        );
      }

      if (json.futuresStarted != null) {
        self.viewModel.futuresStarted(json.futuresStarted);
      }

      var file = json.file;
      if (file) {
        if (file.content) {
          $('#fileContent').html("<strong class='text-primary'>" + file.name + " : " + file.content  + " </strong> " );
        }
        else if (file.error) {
          $('#fileContent').html("<span class='error'>" + file.name + ": " + file.error + "</span>");
        }
      }
    };

    this.subSocket = this.socket.subscribe(this.request);

  },

  addCard: function(card) {
    card.uid = this.uid;

    var message = {
      'action': "addCard",
      'card': card
    };
    this.sendCommand(message);
  },

  startFutures: function() {
    var message = {
      'action': "startFutures"
    };
    this.sendCommand(message);
  },

  stopFutures: function() {
    var message = {
      'action': "stopFutures"
    };
    this.sendCommand(message);
  }

});
//-----------------------------------------------------------------------------

IndexViewModel = Base.extend({
  constructor : function() {
    "use strict";
    var self = this;
    this.base();
    console.log('Initializing index view model');

    // "trello"
    this.totalTrelloCards = ko.observable(0);

    // futures
    this.futuresStarted = ko.observable(false);

    console.log('Initialized Atmosphere');
    var socket = $.atmosphere;

    this.protocol = new TrelloProtocolHandler(socket, self);
    console.log('"Trello" protocol handler attached');
  },

  /*
   "TRELLO"
   */

  addNewCard: function(listId) {
    this.totalTrelloCards(this.totalTrelloCards() + 1);

    var card = {
      no: this.totalTrelloCards(),
      text: 'A card with some text #' + this.totalTrelloCards(),
      listId: listId
    };
    $('#' + listId).append('<div id="card' +  card.no + '"class="bg-danger text-primary">' +  card.text + '</div>');
    this.protocol.addCard(card);
  },
  //-----------------------------------------------------------------------------

  startFutures: function() {
    console.log("Starting futures");
    this.protocol.startFutures();
  },

  stopFutures: function() {
    console.log("Stopping futures");
    this.protocol.stopFutures();
  }

});

