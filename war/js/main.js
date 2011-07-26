(function(window, undefined){
	$(function(){
		$("button.set").click(function(e){
			var data = $(e.target).data();
			var key = data.key;
			var uri = (data.type === "like") ? "/set/like" : "/set/dislike";
			$.getJSON(uri, {key: key});
			location.reload();
		});

		$("button.cancel").click(function(e){
			var data = $(e.target).data();
			console.dir(data);
			var key = data.key;
			var uri = (data.type === "like") ? "/cancel/like" : "/cancel/dislike";
			$.getJSON(uri, {key: key});
			location.reload();
		});
	});
}(this));
