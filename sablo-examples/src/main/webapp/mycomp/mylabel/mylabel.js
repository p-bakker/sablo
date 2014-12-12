angular.module('mylabel',[]).directive('mylabel', function() {  
    return {
      restrict: 'E',
      transclude: true,
      scope: {
			model: "=labelModel"
		},
      controller: function($scope, $element, $attrs) {
			$scope.style = {width:'100%',height:'100%',overflow:'hidden'};
			
			$scope.click = function() { alert('click!'); $scope.model.p = true; }
		  },
      templateUrl: 'mycomp/mylabel/mylabel.html',
      replace: true
    };
  });
