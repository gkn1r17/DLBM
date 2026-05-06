% load("phylo.csv");
% phyloEnv.Properties.VariableNames(2) = "parent";
% phyloEnv.Properties.VariableNames(3) = "child";
% phyloEnv.Var4(1,1) = 0;
% phyloEnv.Properties.VariableNames(4) = "hour";
% phyloEnv.Var5(1,1) = 0;
% phyloEnv.Properties.VariableNames(5) = "location";
% load('W=12_K=1000_MUT=0.001_INIT=1+Phylogeny.mat')

addpath 'C:\Users\gkn1r17\OneDrive - University of Southampton\Documents\Matlab Code'

for i = 1:52
    disp(i);
    [~,y] = ismember(phylo.child, results.tsData{i}.linIDs);
    y2 = find(y > 0);
    phylo.hour(y2) = results.tsData{i}.birthHour(y(y2));
    phylo.location(y2) = results.tsData{i}.origins(y(y2));
end

unfoundIdx = find(phylo.hour == 0 & phylo.parent ~= -1);
unfound = phylo.child(unfoundIdx);

[birthHours, origins] = getBirthHour(unfound, results.diversityTS.mutants, results.diversityTS.daysM, 0);



phylo.hour(unfoundIdx) = birthHours;
phylo.location(unfoundIdx) = origins;
