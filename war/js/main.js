(function(window, undefined){

	var speed = 250;

	$(function(){
		$("img.set").click(function(e){
			var target =$(e.target);
			var data = target.data();
			var key = data.key;
			var isLike = (data.type === "like");
			var uri = isLike ? "/set/like" : "/set/dislike";
			$.getJSON(uri, {key: key}, function(result){
				target.parent().fadeOut(speed, function(){
					if(isLike){
						$("img.cancel[data-key="+key+"][data-type=like]").fadeIn(speed);
					} else {
						$("img.cancel[data-key="+key+"][data-type=dislike]").fadeIn(speed);
					}
				});
			});
		});

		$("img.cancel").click(function(e){
			var target = $(e.target);
			var data = target.data();
			var key = data.key;
			var isLike = (data.type === "like");
			var uri = isLike ? "/cancel/like" : "/cancel/dislike";
			$.getJSON(uri, {key: key}, function(result){
				target.fadeOut(speed, function(){
					$("span[data-key="+key+"]").fadeIn(speed);
				});
			});
		});
	});
}(this));
